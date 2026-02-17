package com.wtfrepo.backend.economy.application;

import com.wtfrepo.backend.arena.application.support.ArenaTradingDayResolver;
import com.wtfrepo.backend.arena.domain.ArenaIpoStatus;
import com.wtfrepo.backend.arena.infra.persistence.entity.EloDailySnapshotJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.EloDailySnapshotJpaRepository;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenRatingJpaRepository;
import com.wtfrepo.backend.economy.application.EconomyWalletService.DeductCommand;
import com.wtfrepo.backend.economy.application.EconomyWalletService.DeductPolicySnapshot;
import com.wtfrepo.backend.economy.application.EconomyWalletService.InsufficientBalanceException;
import com.wtfrepo.backend.economy.application.support.BettingConstants;
import com.wtfrepo.backend.economy.application.support.BettingExceptions;
import com.wtfrepo.backend.economy.application.support.BettingPolicyProperties;
import com.wtfrepo.backend.economy.domain.BetDirection;
import com.wtfrepo.backend.economy.domain.BetOrderStatus;
import com.wtfrepo.backend.economy.domain.BetPoolStatus;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetOrderJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetPoolJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.repository.BetOrderJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.BetPoolJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Betting application service for Phase-B P1 APIs.
 *
 * <p>This service intentionally keeps controller logic thin while enforcing idempotency, cutoff
 * guardrails, and trading-day semantics in one place.
 */
@Service
public class BettingService {

  private static final Logger log = LoggerFactory.getLogger(BettingService.class);

  private final BetPoolJpaRepository betPoolJpaRepository;
  private final BetOrderJpaRepository betOrderJpaRepository;
  private final SpecimenRatingJpaRepository specimenRatingJpaRepository;
  private final SpecimenJpaRepository specimenJpaRepository;
  private final EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository;
  private final EconomyWalletService economyWalletService;
  private final BettingPolicyProperties bettingPolicyProperties;

  public BettingService(
      BetPoolJpaRepository betPoolJpaRepository,
      BetOrderJpaRepository betOrderJpaRepository,
      SpecimenRatingJpaRepository specimenRatingJpaRepository,
      SpecimenJpaRepository specimenJpaRepository,
      EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository,
      EconomyWalletService economyWalletService,
      BettingPolicyProperties bettingPolicyProperties) {
    this.betPoolJpaRepository = betPoolJpaRepository;
    this.betOrderJpaRepository = betOrderJpaRepository;
    this.specimenRatingJpaRepository = specimenRatingJpaRepository;
    this.specimenJpaRepository = specimenJpaRepository;
    this.eloDailySnapshotJpaRepository = eloDailySnapshotJpaRepository;
    this.economyWalletService = economyWalletService;
    this.bettingPolicyProperties = bettingPolicyProperties;
  }

  @Transactional
  public PlaceBetResult placeBet(
      String requestId, String userId, String idempotencyKey, PlaceBetCommand command) {
    return placeBet(requestId, userId, idempotencyKey, command, Instant.now());
  }

  PlaceBetResult placeBet(
      String requestId,
      String userId,
      String idempotencyKey,
      PlaceBetCommand command,
      Instant now) {
    BetDirection direction = BetDirection.parse(command.direction());
    if (direction == null) {
      throw BettingExceptions.invalidDirection(BettingConstants.Message.BET_INVALID_DIRECTION);
    }

    if (command.amount() < bettingPolicyProperties.getMinBet()) {
      throw BettingExceptions.amountTooLow(BettingConstants.Message.BET_AMOUNT_TOO_LOW);
    }

    String specimenId = command.specimenId().trim();
    String requestFingerprint =
        specimenId + "|" + direction.name() + "|" + command.amount() + "|" + userId;
    LocalDate tradingDay = ArenaTradingDayResolver.currentTradingDay(now);

    Optional<BetOrderJpaEntity> existing = betOrderJpaRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return replayOrReject(existing.get(), requestFingerprint);
    }

    SpecimenRatingJpaEntity rating =
        specimenRatingJpaRepository
            .findById(specimenId)
            .orElseThrow(
                () -> BettingExceptions.specimenNotFound(BettingConstants.Message.SPECIMEN_NOT_FOUND));
    if (rating.getIpoStatus() != ArenaIpoStatus.IPO) {
      throw BettingExceptions.ipoLocked(BettingConstants.Message.BET_IPO_LOCKED);
    }

    BetPoolJpaEntity pool =
        betPoolJpaRepository
            .findBySpecimenIdAndDateForUpdate(specimenId, tradingDay)
            .orElseThrow(
                () ->
                    BettingExceptions.poolNotAvailable(
                        BettingConstants.Message.BET_POOL_NOT_AVAILABLE));
    if (pool.getStatus() != BetPoolStatus.OPEN) {
      throw BettingExceptions.poolNotAvailable(BettingConstants.Message.BET_POOL_NOT_AVAILABLE);
    }
    if (!now.isBefore(pool.getBetCutoffAt())) {
      throw BettingExceptions.cutoffPassed(BettingConstants.Message.BET_CUTOFF_PASSED);
    }

    existing = betOrderJpaRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return replayOrReject(existing.get(), requestFingerprint);
    }

    BigDecimal oddsAtPlace = calculateOdds(pool, direction);
    long walletBalanceAfter;
    try {
      walletBalanceAfter =
          economyWalletService.deductBet(
              new DeductCommand(
                  userId,
                  command.amount(),
                  BettingConstants.RefType.BET,
                  idempotencyKey,
                  idempotencyKey,
                  new DeductPolicySnapshot(
                      null,
                      pool.getRakeRate(),
                      bettingPolicyProperties.getMinBet(),
                      bettingPolicyProperties.getPolicyVersion(),
                      bettingPolicyProperties.getPolicySource())));
    } catch (InsufficientBalanceException ex) {
      throw BettingExceptions.insufficientBug(BettingConstants.Message.INSUFFICIENT_BUG);
    }

    BetOrderJpaEntity order =
        BetOrderJpaEntity.createUserOrder(
            userId,
            specimenId,
            direction,
            command.amount(),
            oddsAtPlace,
            tradingDay,
            idempotencyKey,
            requestFingerprint,
            walletBalanceAfter);

    boolean created = true;
    BetOrderJpaEntity persistedOrder = order;
    try {
      betOrderJpaRepository.save(order);
    } catch (DataIntegrityViolationException ex) {
      Optional<BetOrderJpaEntity> byIdempotency =
          betOrderJpaRepository.findByIdempotencyKey(idempotencyKey);
      if (byIdempotency.isEmpty()) {
        throw ex;
      }
      if (!byIdempotency.get().sameRequestFingerprint(requestFingerprint)) {
        throw BettingExceptions.idempotencyConflict(BettingConstants.Message.IDEMPOTENCY_CONFLICT);
      }
      persistedOrder = byIdempotency.get();
      created = false;
    }

    if (!created) {
      return toPlaceBetResult(persistedOrder);
    }

    pool.addToPool(direction, command.amount());
    betPoolJpaRepository.save(pool);

    log.info(
        "bet_place_success requestId={} userId={} specimenId={} direction={} amount={} tradingDay={}",
        requestId,
        userId,
        specimenId,
        direction,
        command.amount(),
        tradingDay);
    return toPlaceBetResult(persistedOrder);
  }

  @Transactional(readOnly = true)
  public ActiveBetsView getActiveBets(String userId) {
    return getActiveBets(userId, Instant.now());
  }

  ActiveBetsView getActiveBets(String userId, Instant now) {
    LocalDate tradingDay = ArenaTradingDayResolver.currentTradingDay(now);
    List<BetOrderJpaEntity> orders =
        betOrderJpaRepository.findByUserIdAndSettleDateAndStatusAndIsHouseFalseOrderByCreatedAtDesc(
            userId, tradingDay, BetOrderStatus.PENDING);
    if (orders.isEmpty()) {
      return new ActiveBetsView(tradingDay, List.of(), 0L);
    }

    Map<String, String> specimenTitles = loadSpecimenTitles(orders);

    List<ActiveBetItem> items = new ArrayList<>(orders.size());
    long totalStaked = 0L;
    for (BetOrderJpaEntity order : orders) {
      Optional<BetPoolJpaEntity> poolOpt =
          betPoolJpaRepository.findBySpecimenIdAndDate(order.getSpecimenId(), tradingDay);
      BigDecimal currentOdds =
          poolOpt.map(pool -> calculateOdds(pool, order.getDirection())).orElse(order.getOddsAtPlace());
      items.add(
          new ActiveBetItem(
              order.getOrderId(),
              order.getSpecimenId(),
              specimenTitles.getOrDefault(order.getSpecimenId(), order.getSpecimenId()),
              order.getDirection().name(),
              order.getAmount(),
              toScale2(order.getOddsAtPlace()),
              toScale2(currentOdds),
              order.getStatus().name()));
      totalStaked += order.getAmount();
    }
    return new ActiveBetsView(tradingDay, items, totalStaked);
  }

  @Transactional(readOnly = true)
  public HistoryBetsView getHistory(String userId, String cursor, Integer limit) {
    int resolvedLimit = resolveHistoryLimit(limit);
    int fetchSize = resolvedLimit + 1;
    PageRequest pageRequest =
        PageRequest.of(0, fetchSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("orderId")));

    List<BetOrderJpaEntity> fetchedOrders =
        resolveHistoryOrders(userId, cursor, pageRequest);
    if (fetchedOrders.isEmpty()) {
      return new HistoryBetsView(List.of(), null, false);
    }

    boolean hasMore = fetchedOrders.size() > resolvedLimit;
    List<BetOrderJpaEntity> orders =
        hasMore ? new ArrayList<>(fetchedOrders.subList(0, resolvedLimit)) : fetchedOrders;

    Map<String, String> specimenTitles = loadSpecimenTitles(orders);
    List<HistoryBetItem> items =
        orders.stream()
            .map(
                order ->
                    new HistoryBetItem(
                        order.getOrderId(),
                        order.getSpecimenId(),
                        specimenTitles.getOrDefault(order.getSpecimenId(), order.getSpecimenId()),
                        order.getDirection().name(),
                        order.getAmount(),
                        toScale2(order.getOddsAtPlace()),
                        order.getStatus().name(),
                        order.getPayout(),
                        order.getMoonDoomBonus() == null ? 0L : order.getMoonDoomBonus(),
                        order.getSettleDate(),
                        order.getSettledAt()))
            .toList();
    String nextCursor = hasMore ? orders.get(orders.size() - 1).getOrderId() : null;
    return new HistoryBetsView(items, nextCursor, hasMore);
  }

  @Transactional
  public BetSummaryView getBetSummary(String specimenId) {
    return getBetSummary(specimenId, Instant.now());
  }

  BetSummaryView getBetSummary(String specimenId, Instant now) {
    String normalizedSpecimenId = specimenId.trim();
    LocalDate tradingDay = ArenaTradingDayResolver.currentTradingDay(now);

    SpecimenRatingJpaEntity rating =
        specimenRatingJpaRepository
            .findById(normalizedSpecimenId)
            .orElseThrow(
                () -> BettingExceptions.specimenNotFound(BettingConstants.Message.SPECIMEN_NOT_FOUND));

    Optional<BetPoolJpaEntity> poolOpt =
        betPoolJpaRepository.findBySpecimenIdAndDate(normalizedSpecimenId, tradingDay);
    if (poolOpt.isEmpty() && canLazyCreatePool(rating, tradingDay, now)) {
      poolOpt = Optional.of(lazyCreatePool(normalizedSpecimenId, tradingDay));
    }

    long totalBettors =
        poolOpt
            .map(
                ignored ->
                    betOrderJpaRepository.countDistinctUserIdBySpecimenIdAndSettleDateAndIsHouseFalse(
                        normalizedSpecimenId, tradingDay))
            .orElse(0L);
    int correctionToday =
        eloDailySnapshotJpaRepository
            .findBySpecimenIdAndDate(normalizedSpecimenId, tradingDay)
            .map(EloDailySnapshotJpaEntity::getGlobalCorrection)
            .orElse(0);

    return buildSummary(normalizedSpecimenId, tradingDay, rating, poolOpt.orElse(null), totalBettors, correctionToday);
  }

  private BetPoolJpaEntity lazyCreatePool(String specimenId, LocalDate tradingDay) {
    Optional<BetPoolJpaEntity> lockedExisting =
        betPoolJpaRepository.findBySpecimenIdAndDateForUpdate(specimenId, tradingDay);
    if (lockedExisting.isPresent()) {
      return lockedExisting.get();
    }

    HouseAllocation allocation = resolveHouseAllocation();
    Instant cutoffAt = cutoffAt(tradingDay);
    BetPoolJpaEntity pool =
        BetPoolJpaEntity.createOpen(
            specimenId,
            tradingDay,
            allocation.houseUp(),
            allocation.houseFlat(),
            allocation.houseDown(),
            bettingPolicyProperties.getRakeRate(),
            cutoffAt);

    try {
      betPoolJpaRepository.saveAndFlush(pool);
    } catch (DataIntegrityViolationException ex) {
      return betPoolJpaRepository
          .findBySpecimenIdAndDate(specimenId, tradingDay)
          .orElseThrow(() -> ex);
    }

    // House liquidity is represented as normal pending orders to keep settlement model symmetric.
    betOrderJpaRepository.saveAll(
        List.of(
            BetOrderJpaEntity.createHouseOrder(
                specimenId,
                BetDirection.UP,
                toIntAmount(allocation.houseUp()),
                calculateOdds(pool, BetDirection.UP),
                tradingDay,
                BettingConstants.User.HOUSE,
                houseIdempotencyKey(specimenId, tradingDay, BetDirection.UP)),
            BetOrderJpaEntity.createHouseOrder(
                specimenId,
                BetDirection.FLAT,
                toIntAmount(allocation.houseFlat()),
                calculateOdds(pool, BetDirection.FLAT),
                tradingDay,
                BettingConstants.User.HOUSE,
                houseIdempotencyKey(specimenId, tradingDay, BetDirection.FLAT)),
            BetOrderJpaEntity.createHouseOrder(
                specimenId,
                BetDirection.DOWN,
                toIntAmount(allocation.houseDown()),
                calculateOdds(pool, BetDirection.DOWN),
                tradingDay,
                BettingConstants.User.HOUSE,
                houseIdempotencyKey(specimenId, tradingDay, BetDirection.DOWN))));
    return pool;
  }

  private PlaceBetResult replayOrReject(BetOrderJpaEntity existing, String requestFingerprint) {
    if (!existing.sameRequestFingerprint(requestFingerprint)) {
      throw BettingExceptions.idempotencyConflict(BettingConstants.Message.IDEMPOTENCY_CONFLICT);
    }
    return toPlaceBetResult(existing);
  }

  private PlaceBetResult toPlaceBetResult(BetOrderJpaEntity order) {
    return new PlaceBetResult(
        order.getOrderId(),
        order.getSpecimenId(),
        order.getDirection().name(),
        order.getAmount(),
        toScale2(order.getOddsAtPlace()),
        order.getSettleDate(),
        order.getStatus().name(),
        order.getWalletBalanceAfter());
  }

  private BetSummaryView buildSummary(
      String specimenId,
      LocalDate tradingDay,
      SpecimenRatingJpaEntity rating,
      BetPoolJpaEntity pool,
      long totalBettors,
      int correctionToday) {
    if (pool == null) {
      return new BetSummaryView(
          specimenId,
          tradingDay,
          0L,
          0L,
          0L,
          0L,
          0L,
          0L,
          null,
          null,
          null,
          bettingPolicyProperties.getRakeRate(),
          null,
          null,
          totalBettors,
          false,
          rating.getIpoStatus().name(),
          rating.getEloScore(),
          rating.getEloOpenToday(),
          rating.getEloScore() - rating.getEloOpenToday(),
          correctionToday,
          resolveMoonDoomThreshold(rating));
    }

    return new BetSummaryView(
        specimenId,
        tradingDay,
        pool.getPoolUp(),
        pool.getPoolFlat(),
        pool.getPoolDown(),
        pool.getHouseUp(),
        pool.getHouseFlat(),
        pool.getHouseDown(),
        toScale2(calculateOdds(pool, BetDirection.UP)),
        toScale2(calculateOdds(pool, BetDirection.FLAT)),
        toScale2(calculateOdds(pool, BetDirection.DOWN)),
        pool.getRakeRate(),
        pool.getBetCutoffAt(),
        pool.getStatus().name(),
        totalBettors,
        pool.getHouseUp() + pool.getHouseFlat() + pool.getHouseDown() > 0,
        rating.getIpoStatus().name(),
        rating.getEloScore(),
        rating.getEloOpenToday(),
        rating.getEloScore() - rating.getEloOpenToday(),
        correctionToday,
        resolveMoonDoomThreshold(rating));
  }

  private Map<String, String> loadSpecimenTitles(List<BetOrderJpaEntity> orders) {
    Set<String> specimenIds =
        orders.stream().map(BetOrderJpaEntity::getSpecimenId).collect(Collectors.toSet());
    return specimenJpaRepository.findBySpecimenIdIn(specimenIds).stream()
        .collect(
            LinkedHashMap::new,
            (map, item) -> map.put(item.getSpecimenId(), item.getRepoFullName()),
            LinkedHashMap::putAll);
  }

  private BigDecimal calculateOdds(BetPoolJpaEntity pool, BetDirection direction) {
    long directionTotal = pool.directionTotalWithHouse(direction);
    if (directionTotal <= 0L) {
      return BigDecimal.ZERO;
    }
    BigDecimal gross = BigDecimal.valueOf(pool.totalPoolWithHouse());
    BigDecimal payoutPool = gross.multiply(BigDecimal.ONE.subtract(pool.getRakeRate()));
    return payoutPool.divide(BigDecimal.valueOf(directionTotal), 4, RoundingMode.HALF_UP);
  }

  private HouseAllocation resolveHouseAllocation() {
    BigDecimal budget = BigDecimal.valueOf(bettingPolicyProperties.getHouseBudget());
    long houseUp =
        budget.multiply(bettingPolicyProperties.getHouseWeightUp()).setScale(0, RoundingMode.HALF_UP).longValue();
    long houseFlat =
        budget
            .multiply(bettingPolicyProperties.getHouseWeightFlat())
            .setScale(0, RoundingMode.HALF_UP)
            .longValue();
    long houseDown = Math.max(0L, bettingPolicyProperties.getHouseBudget() - houseUp - houseFlat);
    return new HouseAllocation(houseUp, houseFlat, houseDown);
  }

  private int resolveMoonDoomThreshold(SpecimenRatingJpaEntity rating) {
    double sigma = Optional.ofNullable(rating.getDeltaR7dStddev()).orElse(0.0D);
    int sigmaThreshold =
        BigDecimal.valueOf(sigma)
            .multiply(bettingPolicyProperties.getMoonDoomSigmaMultiplier())
            .setScale(0, RoundingMode.HALF_UP)
            .intValue();
    return Math.max(sigmaThreshold, rating.getKFactor() * 2);
  }

  private boolean canLazyCreatePool(
      SpecimenRatingJpaEntity rating, LocalDate tradingDay, Instant now) {
    if (rating.getIpoStatus() != ArenaIpoStatus.IPO) {
      return false;
    }
    Instant openAt =
        tradingDay
            .atTime(bettingPolicyProperties.getOpenTimeUtc())
            .atZone(ZoneOffset.UTC)
            .toInstant();
    Instant cutoffAt = cutoffAt(tradingDay);
    return !now.isBefore(openAt) && now.isBefore(cutoffAt);
  }

  private Instant cutoffAt(LocalDate tradingDay) {
    return tradingDay
        .atTime(bettingPolicyProperties.getCutoffTimeUtc())
        .atZone(ZoneOffset.UTC)
        .toInstant();
  }

  private String houseIdempotencyKey(
      String specimenId, LocalDate tradingDay, BetDirection direction) {
    return "house:" + specimenId + ":" + tradingDay + ":" + direction.name();
  }

  private BigDecimal toScale2(BigDecimal rawValue) {
    return rawValue.setScale(2, RoundingMode.HALF_UP);
  }

  private int toIntAmount(long amount) {
    if (amount <= 0) {
      return 0;
    }
    return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
  }

  private int resolveHistoryLimit(Integer limit) {
    int fallback = bettingPolicyProperties.getHistoryDefaultLimit();
    int candidate = (limit == null || limit <= 0) ? fallback : limit;
    return Math.min(candidate, bettingPolicyProperties.getHistoryMaxLimit());
  }

  private List<BetOrderJpaEntity> resolveHistoryOrders(
      String userId, String cursor, PageRequest pageRequest) {
    if (!StringUtils.hasText(cursor)) {
      return betOrderJpaRepository.findByUserIdAndIsHouseFalseOrderByCreatedAtDescOrderIdDesc(
          userId, pageRequest);
    }

    String normalizedCursor = cursor.trim();
    Optional<BetOrderJpaEntity> cursorOrderOpt =
        betOrderJpaRepository
            .findById(normalizedCursor)
            .filter(order -> order.getUserId().equals(userId) && !order.isHouse());
    if (cursorOrderOpt.isEmpty()) {
      return List.of();
    }

    BetOrderJpaEntity cursorOrder = cursorOrderOpt.get();
    return betOrderJpaRepository.findHistoryPageAfterCursor(
        userId, cursorOrder.getCreatedAt(), cursorOrder.getOrderId(), pageRequest);
  }

  public record PlaceBetCommand(String specimenId, String direction, int amount) {}

  public record PlaceBetResult(
      String orderId,
      String specimenId,
      String direction,
      int amount,
      BigDecimal oddsAtPlace,
      LocalDate settleDate,
      String status,
      long walletBalanceAfter) {}

  public record ActiveBetsView(LocalDate date, List<ActiveBetItem> orders, long totalStaked) {}

  public record ActiveBetItem(
      String orderId,
      String specimenId,
      String specimenTitle,
      String direction,
      int amount,
      BigDecimal oddsAtPlace,
      BigDecimal currentOdds,
      String status) {}

  public record HistoryBetsView(List<HistoryBetItem> orders, String nextCursor, boolean hasMore) {}

  public record HistoryBetItem(
      String orderId,
      String specimenId,
      String specimenTitle,
      String direction,
      int amount,
      BigDecimal oddsAtPlace,
      String status,
      Long payout,
      long moonDoomBonus,
      LocalDate settleDate,
      Instant settledAt) {}

  public record BetSummaryView(
      String specimenId,
      LocalDate date,
      long poolUp,
      long poolFlat,
      long poolDown,
      long houseUp,
      long houseFlat,
      long houseDown,
      BigDecimal oddsUp,
      BigDecimal oddsFlat,
      BigDecimal oddsDown,
      BigDecimal rakeRate,
      Instant betCutoffAt,
      String poolStatus,
      long totalBettors,
      boolean houseActive,
      String ipoStatus,
      int currentElo,
      int eloOpenToday,
      int deltaRSoFar,
      int correctionToday,
      int moonDoomThreshold) {}

  private record HouseAllocation(long houseUp, long houseFlat, long houseDown) {}
}
