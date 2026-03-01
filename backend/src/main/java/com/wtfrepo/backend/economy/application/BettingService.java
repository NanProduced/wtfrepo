package com.wtfrepo.backend.economy.application;

import com.wtfrepo.backend.arena.application.support.ArenaTradingDayResolver;
import com.wtfrepo.backend.arena.domain.ArenaIpoStatus;
import com.wtfrepo.backend.arena.infra.persistence.entity.EloDailySnapshotJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.EloDailySnapshotJpaRepository;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenRatingJpaRepository;
import com.wtfrepo.backend.economy.application.EconomyWalletService.CreditCommand;
import com.wtfrepo.backend.economy.application.EconomyWalletService.DeductCommand;
import com.wtfrepo.backend.economy.application.EconomyWalletService.DeductPolicySnapshot;
import com.wtfrepo.backend.economy.application.EconomyWalletService.InsufficientBalanceException;
import com.wtfrepo.backend.economy.application.support.BettingConstants;
import com.wtfrepo.backend.economy.application.support.BettingExceptions;
import com.wtfrepo.backend.economy.application.support.BettingPolicyProperties;
import com.wtfrepo.backend.economy.domain.BetDirection;
import com.wtfrepo.backend.economy.domain.BetOrderStatus;
import com.wtfrepo.backend.economy.domain.BetPoolStatus;
import com.wtfrepo.backend.economy.domain.EconomyLedgerType;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetOrderJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetPoolJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetHouseConfigJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.repository.BetHouseConfigJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.BetOrderJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.BetPoolJpaRepository;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
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
 * Betting application service for public economy APIs.
 *
 * <p>This service intentionally keeps controller logic thin while enforcing idempotency, cutoff
 * guardrails, and trading-day semantics in one place.
 */
@Service
public class BettingService {

  private static final Logger log = LoggerFactory.getLogger(BettingService.class);

  private static final BigDecimal MOON_DOOM_BONUS_RATE = new BigDecimal("0.20");
  private static final Set<BetOrderStatus> SETTLEMENT_ORDER_STATUSES =
      EnumSet.of(BetOrderStatus.WON, BetOrderStatus.LOST, BetOrderStatus.CANCELLED);

  private final BetPoolJpaRepository betPoolJpaRepository;
  private final BetHouseConfigJpaRepository betHouseConfigJpaRepository;
  private final BetOrderJpaRepository betOrderJpaRepository;
  private final SpecimenRatingJpaRepository specimenRatingJpaRepository;
  private final SpecimenJpaRepository specimenJpaRepository;
  private final EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository;
  private final EconomyWalletService economyWalletService;
  private final OutboxEventStore outboxEventStore;
  private final BettingPolicyProperties bettingPolicyProperties;

  public BettingService(
      BetPoolJpaRepository betPoolJpaRepository,
      BetHouseConfigJpaRepository betHouseConfigJpaRepository,
      BetOrderJpaRepository betOrderJpaRepository,
      SpecimenRatingJpaRepository specimenRatingJpaRepository,
      SpecimenJpaRepository specimenJpaRepository,
      EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository,
      EconomyWalletService economyWalletService,
      OutboxEventStore outboxEventStore,
      BettingPolicyProperties bettingPolicyProperties) {
    this.betPoolJpaRepository = betPoolJpaRepository;
    this.betHouseConfigJpaRepository = betHouseConfigJpaRepository;
    this.betOrderJpaRepository = betOrderJpaRepository;
    this.specimenRatingJpaRepository = specimenRatingJpaRepository;
    this.specimenJpaRepository = specimenJpaRepository;
    this.eloDailySnapshotJpaRepository = eloDailySnapshotJpaRepository;
    this.economyWalletService = economyWalletService;
    this.outboxEventStore = outboxEventStore;
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

  @Transactional(readOnly = true)
  public SettlementTodayView getSettlementToday(String userId, String cursor, Integer limit) {
    return getSettlementToday(userId, cursor, limit, Instant.now());
  }

  SettlementTodayView getSettlementToday(String userId, String cursor, Integer limit, Instant now) {
    Instant queryNow = now == null ? Instant.now() : now;
    LocalDate tradingDay = ArenaTradingDayResolver.settlementTradingDay(queryNow);
    int resolvedLimit = resolveHistoryLimit(limit);
    int fetchSize = resolvedLimit + 1;
    PageRequest pageRequest =
        PageRequest.of(
            0, fetchSize, Sort.by(Sort.Order.desc("settledAt"), Sort.Order.desc("orderId")));

    List<BetOrderJpaEntity> fetchedOrders =
        resolveSettlementOrders(userId, tradingDay, cursor, pageRequest);
    if (fetchedOrders.isEmpty()) {
      return new SettlementTodayView(tradingDay, List.of(), null, false);
    }

    boolean hasMore = fetchedOrders.size() > resolvedLimit;
    List<BetOrderJpaEntity> orders =
        hasMore ? new ArrayList<>(fetchedOrders.subList(0, resolvedLimit)) : fetchedOrders;

    Map<String, String> specimenTitles = loadSpecimenTitles(orders);
    Map<String, EloDailySnapshotJpaEntity> snapshotsBySpecimenId =
        loadSettlementSnapshots(orders, tradingDay);
    Map<String, List<BetOrderJpaEntity>> ordersBySpecimenId =
        orders.stream()
            .collect(
                Collectors.groupingBy(
                    BetOrderJpaEntity::getSpecimenId, LinkedHashMap::new, Collectors.toList()));

    List<SettlementItem> settlements =
        ordersBySpecimenId.entrySet().stream()
            .map(
                entry ->
                    toSettlementItem(
                        entry.getKey(),
                        entry.getValue(),
                        specimenTitles,
                        snapshotsBySpecimenId))
            .toList();
    String nextCursor = hasMore ? orders.get(orders.size() - 1).getOrderId() : null;
    return new SettlementTodayView(tradingDay, settlements, nextCursor, hasMore);
  }

  @Transactional
  public BetSummaryView getBetSummary(String specimenId) {
    return getBetSummary(specimenId, Instant.now());
  }

  /**
   * Consumes {@code IpoCompletedEvent} inside betting domain boundary.
   *
   * <p>This method only manages betting-owned read/write data ({@code bet_pool}/{@code bet_order})
   * and intentionally never mutates Arena-owned {@code specimen_rating} fields.
   */
  @Transactional
  public IpoPoolInitResult initializePoolFromIpoEvent(
      String specimenId, Integer calibratedScore, Instant eventOccurredAt) {
    if (!StringUtils.hasText(specimenId)) {
      return new IpoPoolInitResult(null, false, "INVALID_SPECIMEN_ID");
    }

    String normalizedSpecimenId = specimenId.trim();
    Instant now = eventOccurredAt == null ? Instant.now() : eventOccurredAt;
    LocalDate tradingDay = ArenaTradingDayResolver.currentTradingDay(now);

    Optional<BetPoolJpaEntity> existingPool =
        betPoolJpaRepository.findBySpecimenIdAndDate(normalizedSpecimenId, tradingDay);
    if (existingPool.isPresent()) {
      return new IpoPoolInitResult(tradingDay, false, "POOL_ALREADY_EXISTS");
    }

    Optional<SpecimenRatingJpaEntity> rating =
        specimenRatingJpaRepository.findById(normalizedSpecimenId);
    if (rating.isEmpty()) {
      return new IpoPoolInitResult(tradingDay, false, "SPECIMEN_RATING_NOT_FOUND");
    }

    if (!canLazyCreatePool(rating.get(), tradingDay, now)) {
      return new IpoPoolInitResult(tradingDay, false, outcomeForIneligibleIpo(rating.get()));
    }

    lazyCreatePool(normalizedSpecimenId, tradingDay);
    return new IpoPoolInitResult(tradingDay, true, "POOL_CREATED");
  }

  /**
   * Executes force-settle write path for {@code SpecimenDeactivatedEvent}.
   *
   * <p>Contract-aligned behavior:
   *
   * <ul>
   *   <li>only OPEN/CLOSED trading-day pool is eligible;
   *   <li>all PENDING orders are marked CANCELLED;
   *   <li>non-house orders receive full principal refund via wallet ledger;
   *   <li>pool status is moved to SETTLED.
   * </ul>
   */
  @Transactional
  public ForceSettleResult forceSettleBySpecimenDeactivated(
      String eventId, String specimenId, Instant eventOccurredAt) {
    if (!StringUtils.hasText(specimenId)) {
      return new ForceSettleResult(
          null, "INVALID_SPECIMEN_ID", null, null, 0, 0, 0L, false);
    }

    String normalizedSpecimenId = specimenId.trim();
    Instant now = eventOccurredAt == null ? Instant.now() : eventOccurredAt;
    LocalDate tradingDay = ArenaTradingDayResolver.currentTradingDay(now);
    return forceSettleInternal(
        "SPECIMEN_DEACTIVATED_EVENT", eventId, normalizedSpecimenId, tradingDay, now);
  }

  /**
   * Executes final fallback force-settle for one trading-day pool.
   *
   * <p>When settlement retries are exhausted (or retry deadline is reached), contract requires
   * force-settle with full principal refund for user pending orders.
   */
  @Transactional
  public ForceSettleResult forceSettleAfterRetryExhausted(
      String specimenId, LocalDate tradingDay, Instant executionAt, String triggerReason) {
    if (!StringUtils.hasText(specimenId)) {
      return new ForceSettleResult(
          tradingDay, "INVALID_SPECIMEN_ID", null, null, 0, 0, 0L, false);
    }
    if (tradingDay == null) {
      return new ForceSettleResult(
          null, "INVALID_TRADING_DAY", null, null, 0, 0, 0L, false);
    }

    String normalizedSpecimenId = specimenId.trim();
    Instant now = executionAt == null ? Instant.now() : executionAt;
    String normalizedReason =
        StringUtils.hasText(triggerReason) ? triggerReason.trim() : "SETTLEMENT_RETRY_EXHAUSTED";
    return forceSettleInternal(
        "SETTLEMENT_RETRY_FALLBACK", normalizedReason, normalizedSpecimenId, tradingDay, now);
  }

  /**
   * Executes admin-triggered force-settle for the current trading day.
   *
   * <p>Contract-aligned behavior: all pending orders are cancelled and refunded in full.
   */
  @Transactional
  public ForceSettleResult forceSettleByAdmin(
      String specimenId, String reason, Instant executionAt) {
    if (!StringUtils.hasText(specimenId)) {
      return new ForceSettleResult(
          null, "INVALID_SPECIMEN_ID", null, null, 0, 0, 0L, false);
    }
    String normalizedSpecimenId = specimenId.trim();
    Instant now = executionAt == null ? Instant.now() : executionAt;
    LocalDate tradingDay = ArenaTradingDayResolver.currentTradingDay(now);
    String normalizedReason =
        StringUtils.hasText(reason) ? reason.trim() : "ADMIN_FORCE_SETTLE";
    return forceSettleInternal(
        "ADMIN_FORCE_SETTLE", normalizedReason, normalizedSpecimenId, tradingDay, now);
  }

  private ForceSettleResult forceSettleInternal(
      String source,
      String sourceRef,
      String normalizedSpecimenId,
      LocalDate tradingDay,
      Instant executionAt) {

    Optional<BetPoolJpaEntity> poolOpt =
        betPoolJpaRepository.findBySpecimenIdAndDateForUpdate(normalizedSpecimenId, tradingDay);
    if (poolOpt.isEmpty()) {
      return new ForceSettleResult(
          tradingDay, "POOL_MISSING", null, null, 0, 0, 0L, false);
    }

    BetPoolJpaEntity pool = poolOpt.get();
    BetPoolStatus poolStatusBefore = pool.getStatus();
    if (!isForceSettleCandidatePoolStatus(poolStatusBefore)) {
      return new ForceSettleResult(
          tradingDay,
          "POOL_ALREADY_SETTLED",
          poolStatusBefore.name(),
          poolStatusBefore.name(),
          0,
          0,
          0L,
          false);
    }

    List<BetOrderJpaEntity> pendingOrders =
        betOrderJpaRepository.findBySpecimenIdAndSettleDateAndStatusOrderByCreatedAtAscOrderIdAsc(
            normalizedSpecimenId, tradingDay, BetOrderStatus.PENDING);
    if (pendingOrders.isEmpty()) {
      pool.markSettled();
      betPoolJpaRepository.save(pool);
      return new ForceSettleResult(
          tradingDay,
          "POOL_SETTLED_WITHOUT_PENDING_ORDERS",
          poolStatusBefore.name(),
          pool.getStatus().name(),
          0,
          0,
          0L,
          true);
    }

    int refundedOrders = 0;
    long refundedAmount = 0L;
    for (BetOrderJpaEntity pendingOrder : pendingOrders) {
      pendingOrder.markCancelledForForceSettle(executionAt);
      if (pendingOrder.isHouse()) {
        continue;
      }

      economyWalletService.credit(
          new CreditCommand(
              pendingOrder.getUserId(),
              pendingOrder.getAmount(),
              BettingConstants.RefType.BET,
              pendingOrder.getOrderId(),
              forceSettleRefundIdempotencyKey(pendingOrder.getOrderId()),
              bettingPolicyProperties.getPolicyVersion(),
              bettingPolicyProperties.getPolicySource()),
          EconomyLedgerType.FORCE_SETTLE_REFUND);
      appendBetOrderSettledOutboxEvent(
          pendingOrder, "FORCE_SETTLED", false, executionAt);
      refundedOrders++;
      refundedAmount += pendingOrder.getAmount();
    }

    betOrderJpaRepository.saveAll(pendingOrders);
    pool.markSettled();
    betPoolJpaRepository.save(pool);

    log.info(
        "betting_force_settle_completed source={} sourceRef={} specimenId={} tradingDay={} poolStatusBefore={} cancelledOrders={} refundedOrders={} refundedAmount={}",
        source,
        sourceRef,
        normalizedSpecimenId,
        tradingDay,
        poolStatusBefore,
        pendingOrders.size(),
        refundedOrders,
        refundedAmount);

    return new ForceSettleResult(
        tradingDay,
        "FORCE_SETTLED",
        poolStatusBefore.name(),
        pool.getStatus().name(),
        pendingOrders.size(),
        refundedOrders,
        refundedAmount,
        true);
  }

  /**
   * Settlement write path for one CLOSED pool.
   *
   * <p>Execution scope stays strictly inside M03 betting boundary:
   *
   * <ul>
   *   <li>locks target pool row and validates `status=CLOSED` precondition;
   *   <li>reads Arena daily snapshot as settlement single-source-of-truth;
   *   <li>settles all PENDING orders in one transaction and writes wallet ledgers;
   *   <li>marks pool `SETTLED` and appends settlement outbox events.
   * </ul>
   */
  @Transactional
  public ClosedPoolSettlementResult settleClosedPool(
      String specimenId, LocalDate tradingDay, Instant executionAt) {
    if (!StringUtils.hasText(specimenId) || tradingDay == null) {
      return new ClosedPoolSettlementResult(tradingDay, specimenId, "INVALID_INPUT", false, false);
    }

    String normalizedSpecimenId = specimenId.trim();
    Instant now = executionAt == null ? Instant.now() : executionAt;

    Optional<BetPoolJpaEntity> poolOpt =
        betPoolJpaRepository.findBySpecimenIdAndDateForUpdate(normalizedSpecimenId, tradingDay);
    if (poolOpt.isEmpty()) {
      return new ClosedPoolSettlementResult(
          tradingDay, normalizedSpecimenId, "POOL_MISSING", false, false);
    }

    BetPoolJpaEntity pool = poolOpt.get();
    if (pool.getStatus() != BetPoolStatus.CLOSED) {
      return new ClosedPoolSettlementResult(
          tradingDay,
          normalizedSpecimenId,
          "POOL_NOT_CLOSED_" + pool.getStatus().name(),
          false,
          false);
    }

    Optional<EloDailySnapshotJpaEntity> snapshotOpt =
        eloDailySnapshotJpaRepository.findBySpecimenIdAndDate(normalizedSpecimenId, tradingDay);
    if (snapshotOpt.isEmpty()) {
      log.warn(
          "betting_settlement_snapshot_missing specimenId={} tradingDay={} executionAt={}",
          normalizedSpecimenId,
          tradingDay,
          now);
      return new ClosedPoolSettlementResult(
          tradingDay, normalizedSpecimenId, "SNAPSHOT_MISSING", false, false);
    }

    EloDailySnapshotJpaEntity snapshot = snapshotOpt.get();
    int deltaR = snapshot.getDeltaR();
    BetDirection outcomeDirection = resolveSettlementOutcome(deltaR);
    int moonDoomThreshold =
        specimenRatingJpaRepository
            .findById(normalizedSpecimenId)
            .map(this::resolveMoonDoomThreshold)
            .orElse(Integer.MAX_VALUE);

    List<BetOrderJpaEntity> pendingOrders =
        betOrderJpaRepository.findBySpecimenIdAndSettleDateAndStatusOrderByCreatedAtAscOrderIdAsc(
            normalizedSpecimenId, tradingDay, BetOrderStatus.PENDING);
    if (pendingOrders.isEmpty()) {
      pool.markSettled();
      betPoolJpaRepository.save(pool);
      appendSettlementOutboxEvents(
          normalizedSpecimenId,
          tradingDay,
          outcomeDirection,
          0L,
          0L,
          0L,
          deltaR,
          moonDoomThreshold,
          now);
      return new ClosedPoolSettlementResult(
          tradingDay,
          normalizedSpecimenId,
          "POOL_SETTLED_WITHOUT_PENDING_ORDERS",
          true,
          true);
    }

    if (pendingOrders.stream().noneMatch(order -> !order.isHouse())) {
      for (BetOrderJpaEntity pendingOrder : pendingOrders) {
        pendingOrder.markLost(now);
      }
      pool.markSettled();
      betPoolJpaRepository.save(pool);
      appendSettlementOutboxEvents(
          normalizedSpecimenId,
          tradingDay,
          outcomeDirection,
          0L,
          0L,
          0L,
          deltaR,
          moonDoomThreshold,
          now);
      return new ClosedPoolSettlementResult(
          tradingDay, normalizedSpecimenId, "POOL_SETTLED_WITHOUT_USER_ORDERS", true, true);
    }

    List<BetOrderJpaEntity> winningOrders =
        pendingOrders.stream()
            .filter(order -> order.getDirection() == outcomeDirection)
            .toList();
    long winningStakeTotal =
        winningOrders.stream().mapToLong(BetOrderJpaEntity::getAmount).sum();
    long expectedWinningStake = pool.directionTotalWithHouse(outcomeDirection);
    if (winningStakeTotal != expectedWinningStake) {
      log.error(
          "betting_settlement_winning_order_inconsistent specimenId={} tradingDay={} outcome={} winningStakeTotal={} expectedWinningStake={} pendingOrders={}",
          normalizedSpecimenId,
          tradingDay,
          outcomeDirection,
          winningStakeTotal,
          expectedWinningStake,
          pendingOrders.size());
      return new ClosedPoolSettlementResult(
          tradingDay, normalizedSpecimenId, "WINNING_ORDERS_INCONSISTENT", true, false);
    }

    long grossPool = pool.totalPoolWithHouse();
    long payoutPool = resolvePayoutPoolAmount(grossPool, pool.getRakeRate());
    long totalRake = Math.max(0L, grossPool - payoutPool);
    Map<String, Long> payoutByOrderId = allocateParimutuelPayouts(winningOrders, payoutPool);

    boolean moonDoomTriggered = Math.abs((long) deltaR) > moonDoomThreshold;
    String poolRefId = poolRefId(normalizedSpecimenId, tradingDay);

    int wonOrders = 0;
    int lostOrders = 0;
    long totalPayout = 0L;
    long moonDoomBonusTotal = 0L;
    for (BetOrderJpaEntity pendingOrder : pendingOrders) {
      if (pendingOrder.getDirection() != outcomeDirection) {
        pendingOrder.markLost(now);
        lostOrders++;
        if (!pendingOrder.isHouse()) {
          appendBetOrderSettledOutboxEvent(
              pendingOrder, outcomeDirection.name(), false, now);
        }
        continue;
      }

      long payout = payoutByOrderId.getOrDefault(pendingOrder.getOrderId(), 0L);
      long moonDoomBonus = 0L;

      if (pendingOrder.isHouse()) {
        creditHouseWin(pendingOrder.getOrderId(), payout, poolRefId);
      } else {
        creditBetWin(pendingOrder, payout);
        if (moonDoomTriggered && payout > 0L) {
          moonDoomBonus = resolveMoonDoomBonusAmount(payout);
          creditMoonDoomBonus(pendingOrder, moonDoomBonus, poolRefId);
        }
      }

      pendingOrder.markWon(payout, moonDoomBonus, now);
      wonOrders++;
      totalPayout += payout;
      moonDoomBonusTotal += moonDoomBonus;
      if (!pendingOrder.isHouse()) {
        appendBetOrderSettledOutboxEvent(
            pendingOrder, outcomeDirection.name(), moonDoomBonus > 0L, now);
      }
    }

    creditRake(totalRake, poolRefId);
    pool.markSettled();
    betPoolJpaRepository.save(pool);

    appendSettlementOutboxEvents(
        normalizedSpecimenId,
        tradingDay,
        outcomeDirection,
        totalPayout,
        totalRake,
        moonDoomBonusTotal,
        deltaR,
        moonDoomThreshold,
        now);

    log.info(
        "betting_settlement_completed specimenId={} tradingDay={} outcome={} wonOrders={} lostOrders={} totalPayout={} totalRake={} moonDoomTriggered={} moonDoomBonusTotal={} executionAt={}",
        normalizedSpecimenId,
        tradingDay,
        outcomeDirection,
        wonOrders,
        lostOrders,
        totalPayout,
        totalRake,
        moonDoomTriggered,
        moonDoomBonusTotal,
        now);
    return new ClosedPoolSettlementResult(
        tradingDay, normalizedSpecimenId, "SETTLED_" + outcomeDirection.name(), true, true);
  }

  /** Compatibility bridge kept for already wired skeleton callers. */
  @Transactional
  public ClosedPoolSettlementResult settleClosedPoolSkeleton(
      String specimenId, LocalDate tradingDay, Instant executionAt) {
    return settleClosedPool(specimenId, tradingDay, executionAt);
  }

  /**
   * Read-only candidate inspection helper retained for diagnostics and operational checks.
   */
  @Transactional(readOnly = true)
  public ForceSettleCandidateView inspectForceSettleCandidate(
      String specimenId, Instant eventOccurredAt) {
    if (!StringUtils.hasText(specimenId)) {
      return new ForceSettleCandidateView(null, "INVALID_SPECIMEN_ID", false);
    }

    String normalizedSpecimenId = specimenId.trim();
    Instant now = eventOccurredAt == null ? Instant.now() : eventOccurredAt;
    LocalDate tradingDay = ArenaTradingDayResolver.currentTradingDay(now);

    Optional<BetPoolJpaEntity> pool =
        betPoolJpaRepository.findBySpecimenIdAndDate(normalizedSpecimenId, tradingDay);
    if (pool.isEmpty()) {
      return new ForceSettleCandidateView(tradingDay, "POOL_MISSING", false);
    }

    BetPoolStatus status = pool.get().getStatus();
    return new ForceSettleCandidateView(
        tradingDay, status.name(), isForceSettleCandidatePoolStatus(status));
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

    HouseAllocation allocation = resolveHouseAllocation(specimenId);
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

  private HouseAllocation resolveHouseAllocation(String specimenId) {
    HouseConfigRecord config = findHouseConfig(specimenId);
    BigDecimal budget =
        BigDecimal.valueOf(
            config != null ? config.houseBudget() : bettingPolicyProperties.getHouseBudget());
    BigDecimal weightUp =
        config != null ? config.weightUp() : bettingPolicyProperties.getHouseWeightUp();
    BigDecimal weightFlat =
        config != null ? config.weightFlat() : bettingPolicyProperties.getHouseWeightFlat();
    BigDecimal weightDown =
        config != null ? config.weightDown() : bettingPolicyProperties.getHouseWeightDown();

    long houseUp = budget.multiply(weightUp).setScale(0, RoundingMode.HALF_UP).longValue();
    long houseFlat = budget.multiply(weightFlat).setScale(0, RoundingMode.HALF_UP).longValue();
    long houseDown = budget.multiply(weightDown).setScale(0, RoundingMode.HALF_UP).longValue();
    long roundingDelta = budget.longValue() - houseUp - houseFlat - houseDown;
    if (roundingDelta != 0L) {
      houseDown = Math.max(0L, houseDown + roundingDelta);
    }
    return new HouseAllocation(houseUp, houseFlat, houseDown);
  }

  @Transactional(readOnly = true)
  public HouseConfigRecord findHouseConfig(String specimenId) {
    if (!StringUtils.hasText(specimenId)) {
      return null;
    }
    return betHouseConfigJpaRepository
        .findBySpecimenId(specimenId.trim())
        .map(this::toHouseConfigRecord)
        .orElse(null);
  }

  @Transactional
  public HouseConfigRecord updateHouseConfig(
      String specimenId,
      long houseBudget,
      BigDecimal weightUp,
      BigDecimal weightFlat,
      BigDecimal weightDown,
      String updatedBy) {
    String normalizedSpecimenId = specimenId.trim();
    if (specimenJpaRepository.findById(normalizedSpecimenId).isEmpty()) {
      throw BettingExceptions.specimenNotFound(BettingConstants.Message.SPECIMEN_NOT_FOUND);
    }

    BetHouseConfigJpaEntity entity =
        betHouseConfigJpaRepository.findBySpecimenId(normalizedSpecimenId).orElse(null);
    if (entity == null) {
      entity =
          BetHouseConfigJpaEntity.create(
              normalizedSpecimenId,
              houseBudget,
              weightUp,
              weightFlat,
              weightDown,
              updatedBy);
    } else {
      entity.update(houseBudget, weightUp, weightFlat, weightDown, updatedBy);
    }

    BetHouseConfigJpaEntity saved = betHouseConfigJpaRepository.save(entity);
    return toHouseConfigRecord(saved);
  }

  private HouseConfigRecord toHouseConfigRecord(BetHouseConfigJpaEntity entity) {
    if (entity == null) {
      return null;
    }
    return new HouseConfigRecord(
        entity.getSpecimenId(),
        entity.getHouseBudget(),
        entity.getWeightUp(),
        entity.getWeightFlat(),
        entity.getWeightDown(),
        entity.getUpdatedBy(),
        entity.getUpdatedAt(),
        entity.getCreatedAt());
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

  private String outcomeForIneligibleIpo(SpecimenRatingJpaEntity rating) {
    if (rating.getIpoStatus() != ArenaIpoStatus.IPO) {
      return "SPECIMEN_NOT_IN_IPO";
    }
    return "OUTSIDE_BET_WINDOW";
  }

  private BetDirection resolveSettlementOutcome(int deltaR) {
    if (deltaR > bettingPolicyProperties.getDeadZoneThreshold()) {
      return BetDirection.UP;
    }
    if (deltaR < -bettingPolicyProperties.getDeadZoneThreshold()) {
      return BetDirection.DOWN;
    }
    return BetDirection.FLAT;
  }

  /**
   * Computes deterministic parimutuel payout allocation and guarantees sum equals {@code payoutPool}.
   */
  private Map<String, Long> allocateParimutuelPayouts(
      List<BetOrderJpaEntity> winningOrders, long payoutPool) {
    if (winningOrders.isEmpty() || payoutPool <= 0L) {
      return Map.of();
    }

    long winningStakeTotal = winningOrders.stream().mapToLong(BetOrderJpaEntity::getAmount).sum();
    if (winningStakeTotal <= 0L) {
      return Map.of();
    }

    Map<String, Long> payouts = new LinkedHashMap<>();
    List<PayoutRemainderCandidate> remainderCandidates = new ArrayList<>(winningOrders.size());

    long allocated = 0L;
    for (BetOrderJpaEntity winningOrder : winningOrders) {
      BigDecimal rawShare =
          BigDecimal.valueOf(payoutPool)
              .multiply(BigDecimal.valueOf(winningOrder.getAmount()))
              .divide(BigDecimal.valueOf(winningStakeTotal), 8, RoundingMode.DOWN);
      long roundedShare = rawShare.setScale(0, RoundingMode.DOWN).longValue();
      payouts.put(winningOrder.getOrderId(), roundedShare);
      remainderCandidates.add(
          new PayoutRemainderCandidate(
              winningOrder.getOrderId(), rawShare.subtract(BigDecimal.valueOf(roundedShare))));
      allocated += roundedShare;
    }

    long remainder = payoutPool - allocated;
    if (remainder <= 0L || remainderCandidates.isEmpty()) {
      return payouts;
    }

    remainderCandidates.sort(
        Comparator.comparing(PayoutRemainderCandidate::fraction)
            .reversed()
            .thenComparing(PayoutRemainderCandidate::orderId));
    for (long i = 0; i < remainder; i++) {
      PayoutRemainderCandidate candidate =
          remainderCandidates.get((int) (i % remainderCandidates.size()));
      payouts.computeIfPresent(candidate.orderId(), (ignored, value) -> value + 1L);
    }
    return payouts;
  }

  private long resolvePayoutPoolAmount(long grossPool, BigDecimal rakeRate) {
    if (grossPool <= 0L) {
      return 0L;
    }

    BigDecimal effectiveRakeRate = rakeRate == null ? BigDecimal.ZERO : rakeRate;
    if (effectiveRakeRate.compareTo(BigDecimal.ZERO) < 0) {
      effectiveRakeRate = BigDecimal.ZERO;
    }
    if (effectiveRakeRate.compareTo(BigDecimal.ONE) > 0) {
      effectiveRakeRate = BigDecimal.ONE;
    }

    BigDecimal payoutRate = BigDecimal.ONE.subtract(effectiveRakeRate);
    return BigDecimal.valueOf(grossPool)
        .multiply(payoutRate)
        .setScale(0, RoundingMode.DOWN)
        .longValue();
  }

  private long resolveMoonDoomBonusAmount(long payout) {
    if (payout <= 0L) {
      return 0L;
    }
    return BigDecimal.valueOf(payout)
        .multiply(MOON_DOOM_BONUS_RATE)
        .setScale(0, RoundingMode.HALF_UP)
        .longValue();
  }

  private void creditBetWin(BetOrderJpaEntity order, long payout) {
    if (payout <= 0L) {
      return;
    }
    economyWalletService.credit(
        new CreditCommand(
            order.getUserId(),
            toCreditAmount(payout),
            BettingConstants.RefType.BET,
            order.getOrderId(),
            betWinIdempotencyKey(order.getOrderId()),
            bettingPolicyProperties.getPolicyVersion(),
            bettingPolicyProperties.getPolicySource()),
        EconomyLedgerType.BET_WIN);
  }

  private void creditHouseWin(String orderId, long payout, String poolRefId) {
    if (payout <= 0L) {
      return;
    }
    economyWalletService.credit(
        new CreditCommand(
            BettingConstants.User.HOUSE,
            toCreditAmount(payout),
            BettingConstants.RefType.SYSTEM,
            poolRefId,
            houseWinIdempotencyKey(orderId),
            bettingPolicyProperties.getPolicyVersion(),
            bettingPolicyProperties.getPolicySource()),
        EconomyLedgerType.HOUSE_WIN);
  }

  private void creditMoonDoomBonus(BetOrderJpaEntity order, long bonus, String poolRefId) {
    if (bonus <= 0L) {
      return;
    }
    economyWalletService.credit(
        new CreditCommand(
            order.getUserId(),
            toCreditAmount(bonus),
            BettingConstants.RefType.SYSTEM,
            poolRefId,
            moonDoomIdempotencyKey(order.getOrderId()),
            bettingPolicyProperties.getPolicyVersion(),
            bettingPolicyProperties.getPolicySource()),
        EconomyLedgerType.MOON_DOOM_BONUS);
  }

  private void creditRake(long totalRake, String poolRefId) {
    if (totalRake <= 0L) {
      return;
    }
    economyWalletService.credit(
        new CreditCommand(
            BettingConstants.User.HOUSE,
            toCreditAmount(totalRake),
            BettingConstants.RefType.SYSTEM,
            poolRefId,
            rakeIdempotencyKey(poolRefId),
            bettingPolicyProperties.getPolicyVersion(),
            bettingPolicyProperties.getPolicySource()),
        EconomyLedgerType.RAKE);
  }

  private int toCreditAmount(long amount) {
    if (amount <= 0L) {
      return 0;
    }
    if (amount > Integer.MAX_VALUE) {
      throw new IllegalStateException("Settlement amount exceeds int range: " + amount);
    }
    return (int) amount;
  }

  private void appendSettlementOutboxEvents(
      String specimenId,
      LocalDate tradingDay,
      BetDirection outcomeDirection,
      long totalPayout,
      long totalRake,
      long moonDoomBonusTotal,
      int deltaR,
      int moonDoomThreshold,
      Instant occurredAt) {
    String poolRefId = poolRefId(specimenId, tradingDay);
    outboxEventStore.append(
        new OutboxEventCommand(
            "BET_POOL",
            poolRefId,
            "SettlementCompletedEvent",
            settlementCompletedEventKey(specimenId, tradingDay),
            new SettlementCompletedEventPayload(
                specimenId,
                tradingDay,
                outcomeDirection.name(),
                totalPayout,
                totalRake,
                moonDoomBonusTotal > 0L),
            occurredAt));

    if (moonDoomBonusTotal <= 0L) {
      return;
    }

    outboxEventStore.append(
        new OutboxEventCommand(
            "BET_POOL",
            poolRefId,
            "MoonDoomEvent",
            moonDoomEventKey(specimenId, tradingDay),
            new MoonDoomEventPayload(specimenId, deltaR, moonDoomThreshold, moonDoomBonusTotal),
            occurredAt));
  }

  private void appendBetOrderSettledOutboxEvent(
      BetOrderJpaEntity order, String outcome, boolean isMoonDoom, Instant occurredAt) {
    if (order == null || order.isHouse()) {
      return;
    }

    String normalizedOutcome =
        StringUtils.hasText(outcome) ? outcome.trim() : "UNKNOWN";
    long payout = order.getPayout() == null ? 0L : order.getPayout();
    outboxEventStore.append(
        new OutboxEventCommand(
            "BET_ORDER",
            order.getOrderId(),
            "BetOrderSettledEvent",
            betOrderSettledEventKey(order.getOrderId()),
            new BetOrderSettledEventPayload(
                order.getOrderId(),
                order.getUserId(),
                order.getSpecimenId(),
                order.getDirection().name(),
                normalizedOutcome,
                payout,
                order.getSettleDate(),
                isMoonDoom),
            occurredAt));
  }

  private boolean isForceSettleCandidatePoolStatus(BetPoolStatus status) {
    return status == BetPoolStatus.OPEN || status == BetPoolStatus.CLOSED;
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

  private String forceSettleRefundIdempotencyKey(String orderId) {
    return "forcesettle_" + orderId;
  }

  private String betWinIdempotencyKey(String orderId) {
    return "betwin_" + orderId;
  }

  private String houseWinIdempotencyKey(String orderId) {
    return "housewin_" + orderId;
  }

  private String moonDoomIdempotencyKey(String orderId) {
    return "moondoom_" + orderId;
  }

  private String rakeIdempotencyKey(String poolRefId) {
    return "rake_" + poolRefId;
  }

  private String poolRefId(String specimenId, LocalDate tradingDay) {
    return specimenId + ":" + tradingDay;
  }

  private String settlementCompletedEventKey(String specimenId, LocalDate tradingDay) {
    return "betting:settlement-completed:" + specimenId + ":" + tradingDay;
  }

  private String moonDoomEventKey(String specimenId, LocalDate tradingDay) {
    return "betting:moon-doom:" + specimenId + ":" + tradingDay;
  }

  private String betOrderSettledEventKey(String orderId) {
    return "betting:order-settled:" + orderId;
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

  private List<BetOrderJpaEntity> resolveSettlementOrders(
      String userId, LocalDate tradingDay, String cursor, PageRequest pageRequest) {
    if (!StringUtils.hasText(cursor)) {
      return betOrderJpaRepository
          .findByUserIdAndSettleDateAndIsHouseFalseAndStatusInOrderBySettledAtDescOrderIdDesc(
              userId, tradingDay, SETTLEMENT_ORDER_STATUSES, pageRequest);
    }

    String normalizedCursor = cursor.trim();
    Optional<BetOrderJpaEntity> cursorOrderOpt =
        betOrderJpaRepository
            .findById(normalizedCursor)
            .filter(
                order ->
                    order.getUserId().equals(userId)
                        && !order.isHouse()
                        && tradingDay.equals(order.getSettleDate())
                        && order.getSettledAt() != null
                        && SETTLEMENT_ORDER_STATUSES.contains(order.getStatus()));
    if (cursorOrderOpt.isEmpty()) {
      return List.of();
    }

    BetOrderJpaEntity cursorOrder = cursorOrderOpt.get();
    return betOrderJpaRepository.findSettlementPageAfterCursor(
        userId,
        tradingDay,
        SETTLEMENT_ORDER_STATUSES,
        cursorOrder.getSettledAt(),
        cursorOrder.getOrderId(),
        pageRequest);
  }

  private Map<String, EloDailySnapshotJpaEntity> loadSettlementSnapshots(
      List<BetOrderJpaEntity> orders, LocalDate tradingDay) {
    Set<String> specimenIds =
        orders.stream().map(BetOrderJpaEntity::getSpecimenId).collect(Collectors.toSet());
    if (specimenIds.isEmpty()) {
      return Map.of();
    }

    return eloDailySnapshotJpaRepository
        .findAllBySpecimenIdInAndDateBetween(specimenIds, tradingDay, tradingDay)
        .stream()
        .collect(
            Collectors.toMap(
                EloDailySnapshotJpaEntity::getSpecimenId,
                snapshot -> snapshot,
                (left, right) -> left,
                LinkedHashMap::new));
  }

  private SettlementItem toSettlementItem(
      String specimenId,
      List<BetOrderJpaEntity> orders,
      Map<String, String> specimenTitles,
      Map<String, EloDailySnapshotJpaEntity> snapshotsBySpecimenId) {
    EloDailySnapshotJpaEntity snapshot = snapshotsBySpecimenId.get(specimenId);
    int eloOpen = snapshot == null ? 0 : snapshot.getEloOpen();
    int eloClose = snapshot == null ? 0 : snapshot.getEloClose();
    int deltaR = snapshot == null ? 0 : snapshot.getDeltaR();

    List<SettlementOrderItem> myOrders =
        orders.stream()
            .map(
                order ->
                    new SettlementOrderItem(
                        order.getOrderId(),
                        order.getDirection().name(),
                        order.getAmount(),
                        order.getStatus().name(),
                        order.getPayout() == null ? 0L : order.getPayout(),
                        order.getMoonDoomBonus() == null ? 0L : order.getMoonDoomBonus()))
            .toList();
    boolean isMoonDoom = myOrders.stream().anyMatch(order -> order.moonDoomBonus() > 0L);

    return new SettlementItem(
        specimenId,
        specimenTitles.getOrDefault(specimenId, specimenId),
        eloOpen,
        eloClose,
        deltaR,
        resolveSettlementOutcome(snapshot, orders),
        isMoonDoom,
        myOrders);
  }

  private String resolveSettlementOutcome(
      EloDailySnapshotJpaEntity snapshot, List<BetOrderJpaEntity> orders) {
    boolean allCancelled = orders.stream().allMatch(order -> order.getStatus() == BetOrderStatus.CANCELLED);
    if (allCancelled) {
      return "FORCE_SETTLED";
    }

    if (snapshot != null) {
      if (snapshot.getDeltaR() > 0) {
        return BetDirection.UP.name();
      }
      if (snapshot.getDeltaR() < 0) {
        return BetDirection.DOWN.name();
      }
      return BetDirection.FLAT.name();
    }

    return orders.stream()
        .filter(order -> order.getStatus() == BetOrderStatus.WON)
        .map(order -> order.getDirection().name())
        .findFirst()
        .orElse("UNKNOWN");
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

  public record SettlementTodayView(
      LocalDate date, List<SettlementItem> settlements, String nextCursor, boolean hasMore) {}

  public record SettlementItem(
      String specimenId,
      String specimenTitle,
      int eloOpen,
      int eloClose,
      int deltaR,
      String outcome,
      boolean isMoonDoom,
      List<SettlementOrderItem> myOrders) {}

  public record SettlementOrderItem(
      String orderId,
      String direction,
      int amount,
      String status,
      long payout,
      long moonDoomBonus) {}

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

  /** Outcome of one {@code IpoCompletedEvent} consumption attempt in betting domain. */
  public record IpoPoolInitResult(LocalDate tradingDay, boolean created, String outcome) {}

  /**
   * Outcome of one force-settle execution attempt triggered by specimen lifecycle events.
   *
   * <p>When {@code settled=true}, betting pool is guaranteed to be in SETTLED status for the
   * target trading day.
   */
  public record ForceSettleResult(
      LocalDate tradingDay,
      String outcome,
      String poolStatusBefore,
      String poolStatusAfter,
      int cancelledOrders,
      int refundedOrders,
      long refundedAmount,
      boolean settled) {}

  /**
   * Outcome of one CLOSED-pool settlement execution.
   *
   * <p>{@code preconditionsPassed=true} means pool lock/state + snapshot checks are satisfied.
   */
  public record ClosedPoolSettlementResult(
      LocalDate tradingDay,
      String specimenId,
      String outcome,
      boolean preconditionsPassed,
      boolean settled) {}

  /** Read-only preview of whether current pool state requires force-settle handling. */
  public record ForceSettleCandidateView(
      LocalDate tradingDay, String poolStatus, boolean forceSettleCandidate) {}

  /** Snapshot of per-specimen house budget configuration. */
  public record HouseConfigRecord(
      String specimenId,
      long houseBudget,
      BigDecimal weightUp,
      BigDecimal weightFlat,
      BigDecimal weightDown,
      String updatedBy,
      Instant updatedAt,
      Instant createdAt) {}

  private record PayoutRemainderCandidate(String orderId, BigDecimal fraction) {}

  private record SettlementCompletedEventPayload(
      String specimenId,
      LocalDate date,
      String outcome,
      long totalPayout,
      long totalRake,
      boolean isMoonDoom) {}

  private record BetOrderSettledEventPayload(
      String orderId,
      String userId,
      String specimenId,
      String direction,
      String outcome,
      long payout,
      LocalDate settleDate,
      boolean isMoonDoom) {}

  private record MoonDoomEventPayload(
      String specimenId, int deltaR, int threshold, long bonusTotal) {}

  private record HouseAllocation(long houseUp, long houseFlat, long houseDown) {}
}
