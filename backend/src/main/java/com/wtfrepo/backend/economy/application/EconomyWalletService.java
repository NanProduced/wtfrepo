package com.wtfrepo.backend.economy.application;

import com.wtfrepo.backend.economy.application.policy.EconomyPolicyPort;
import com.wtfrepo.backend.economy.application.policy.EconomyPolicySnapshot;
import com.wtfrepo.backend.economy.application.support.EconomyConstants;
import com.wtfrepo.backend.economy.application.support.EconomyExceptions;
import com.wtfrepo.backend.economy.domain.EconomyLedgerType;
import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyDailyClaimJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyLedgerJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyWalletJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyDailyClaimJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyLedgerJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyWalletJpaRepository;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Economy wallet core service for balance, ledger, deduction and credit operations. */
@Service
public class EconomyWalletService {

  private static final DateTimeFormatter DAILY_CLAIM_KEY_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final EconomyWalletJpaRepository walletRepository;
  private final EconomyDailyClaimJpaRepository dailyClaimRepository;
  private final EconomyLedgerJpaRepository ledgerRepository;
  private final EconomyWalletBootstrapPort walletBootstrapPort;
  private final EconomyPolicyPort economyPolicyPort;
  private final OutboxEventStore outboxEventStore;

  public EconomyWalletService(
      EconomyWalletJpaRepository walletRepository,
      EconomyDailyClaimJpaRepository dailyClaimRepository,
      EconomyLedgerJpaRepository ledgerRepository,
      EconomyWalletBootstrapPort walletBootstrapPort,
      EconomyPolicyPort economyPolicyPort,
      OutboxEventStore outboxEventStore) {
    this.walletRepository = walletRepository;
    this.dailyClaimRepository = dailyClaimRepository;
    this.ledgerRepository = ledgerRepository;
    this.walletBootstrapPort = walletBootstrapPort;
    this.economyPolicyPort = economyPolicyPort;
    this.outboxEventStore = outboxEventStore;
  }

  @Transactional(readOnly = true)
  public WalletView getWallet(String userId) {
    long balance = currentBalance(userId);
    EconomyPolicySnapshot policySnapshot = economyPolicyPort.currentPolicySnapshot();
    long totalEarned = ledgerRepository.sumEarnedByUserId(userId);
    long totalSpent = ledgerRepository.sumSpentByUserId(userId);
    boolean dailyClaimed =
        dailyClaimRepository
            .findByUserIdAndTradingDay(userId, tradingDayUtc())
            .map(claim -> true)
            .orElse(false);
    return new WalletView(
        userId,
        balance,
        totalEarned,
        totalSpent,
        dailyClaimed,
        policySnapshot.dailyClaimAmount(),
        policySnapshot.voteCost());
  }

  @Transactional(readOnly = true)
  public long currentBalance(String userId) {
    return walletRepository
        .findById(userId)
        .map(wallet -> wallet.getBalance())
        .orElseGet(() -> walletBootstrapPort.initialBalanceFor(userId));
  }

  @Transactional
  public long deduct(DeductCommand command) {
    return deductInternal(command, EconomyLedgerType.ARENA_VOTE_COST);
  }

  @Transactional
  public long deductBet(DeductCommand command) {
    return deductInternal(command, EconomyLedgerType.BET);
  }

  @Transactional
  public long credit(CreditCommand command, EconomyLedgerType ledgerType) {
    validateCreditAmount(command.amount());

    Optional<EconomyLedgerJpaEntity> existingByIdempotency =
        ledgerRepository.findByIdempotencyKey(command.idempotencyKey());
    if (existingByIdempotency.isPresent()) {
      return existingByIdempotency.get().getBalanceAfter();
    }

    EconomyWalletJpaEntity wallet = loadWalletForUpdateOrCreate(command.userId());

    // Re-check idempotency key under wallet lock to avoid duplicate ledger write under concurrency.
    existingByIdempotency = ledgerRepository.findByIdempotencyKey(command.idempotencyKey());
    if (existingByIdempotency.isPresent()) {
      return existingByIdempotency.get().getBalanceAfter();
    }

    long balanceAfter = wallet.credit(command.amount());
    walletRepository.save(wallet);
    ledgerRepository.save(
        EconomyLedgerJpaEntity.create(
            command.userId(),
            ledgerType,
            command.amount(),
            balanceAfter,
            command.refType(),
            command.refId(),
            command.idempotencyKey(),
            null,
            null,
            null,
            command.policyVersion(),
            command.policySource()));
    appendBugBalanceChangedEvent(
        command.userId(),
        command.amount(),
        balanceAfter,
        ledgerType.name(),
        command.refId(),
        command.idempotencyKey());
    return balanceAfter;
  }

  @Transactional(readOnly = true)
  public LedgerPageView getLedger(String userId, String cursor, Integer limit, String reason) {
    int resolvedLimit = resolveLedgerLimit(limit);
    EconomyLedgerType reasonFilter = resolveReasonFilter(reason);

    Instant cursorCreatedAt = null;
    String cursorLedgerId = null;
    if (StringUtils.hasText(cursor)) {
      String normalizedCursor = cursor.trim();
      EconomyLedgerJpaEntity cursorLedger =
          ledgerRepository
              .findById(normalizedCursor)
              .filter(item -> userId.equals(item.getUserId()))
              .orElseThrow(
                  () ->
                      EconomyExceptions.invalidCursor(EconomyConstants.Message.INVALID_CURSOR));
      cursorCreatedAt = cursorLedger.getCreatedAt();
      cursorLedgerId = cursorLedger.getLedgerId();
    }

    List<EconomyLedgerJpaEntity> fetched =
        ledgerRepository.findLedgerPage(
            userId,
            reasonFilter,
            cursorCreatedAt,
            cursorLedgerId,
            PageRequest.of(0, resolvedLimit + 1, Sort.unsorted()));

    boolean hasMore = fetched.size() > resolvedLimit;
    List<EconomyLedgerJpaEntity> pageItems =
        hasMore ? new ArrayList<>(fetched.subList(0, resolvedLimit)) : fetched;

    String nextCursor = hasMore ? pageItems.get(pageItems.size() - 1).getLedgerId() : null;
    List<LedgerItemView> items = pageItems.stream().map(this::toLedgerItemView).toList();
    return new LedgerPageView(items, nextCursor, hasMore);
  }

  private long deductInternal(DeductCommand command, EconomyLedgerType ledgerType) {
    validateDeductAmount(command.amount());

    Optional<EconomyLedgerJpaEntity> existingByIdempotency =
        ledgerRepository.findByIdempotencyKey(command.idempotencyKey());
    if (existingByIdempotency.isPresent()) {
      return existingByIdempotency.get().getBalanceAfter();
    }

    EconomyWalletJpaEntity wallet = loadWalletForUpdateOrCreate(command.userId());

    // Re-check idempotency key under wallet lock to avoid duplicate ledger write under concurrency.
    existingByIdempotency = ledgerRepository.findByIdempotencyKey(command.idempotencyKey());
    if (existingByIdempotency.isPresent()) {
      return existingByIdempotency.get().getBalanceAfter();
    }

    if (wallet.getBalance() < command.amount()) {
      throw new InsufficientBalanceException(command.userId(), wallet.getBalance(), command.amount());
    }

    long balanceAfter = wallet.debit(command.amount());
    walletRepository.save(wallet);
    ledgerRepository.save(
        EconomyLedgerJpaEntity.create(
            command.userId(),
            ledgerType,
            -command.amount(),
            balanceAfter,
            command.refType(),
            command.refId(),
            command.idempotencyKey(),
            command.policySnapshot().bugCost(),
            command.policySnapshot().minBet(),
            command.policySnapshot().rakeRate(),
            command.policySnapshot().policyVersion(),
            command.policySnapshot().policySource()));
    appendBugBalanceChangedEvent(
        command.userId(),
        -command.amount(),
        balanceAfter,
        ledgerType.name(),
        command.refId(),
        command.idempotencyKey());
    return balanceAfter;
  }

  @Transactional
  public DailyClaimResult claimDaily(String userId) {
    LocalDate tradingDay = tradingDayUtc();
    Optional<EconomyDailyClaimJpaEntity> existingClaim =
        dailyClaimRepository.findByUserIdAndTradingDay(userId, tradingDay);
    if (existingClaim.isPresent()) {
      EconomyDailyClaimJpaEntity claim = existingClaim.get();
      return DailyClaimResult.alreadyClaimed(claim.getBalanceAfter());
    }

    EconomyWalletJpaEntity wallet = loadWalletForUpdateOrCreate(userId);

    EconomyPolicySnapshot policySnapshot = economyPolicyPort.currentPolicySnapshot();
    int amount = policySnapshot.dailyClaimAmount();
    long balanceAfter = wallet.getBalance() + amount;

    EconomyDailyClaimJpaEntity claimEntity =
        EconomyDailyClaimJpaEntity.create(
            userId,
            tradingDay,
            amount,
            balanceAfter,
            policySnapshot.policyVersion(),
            policySnapshot.policySource());

    try {
      dailyClaimRepository.saveAndFlush(claimEntity);
    } catch (DataIntegrityViolationException ex) {
      // Another request has already claimed today while this transaction was waiting on lock.
      return dailyClaimRepository
          .findByUserIdAndTradingDay(userId, tradingDay)
          .map(claim -> DailyClaimResult.alreadyClaimed(claim.getBalanceAfter()))
          .orElseThrow(() -> ex);
    }

    wallet.credit(amount);
    walletRepository.save(wallet);
    ledgerRepository.save(
        EconomyLedgerJpaEntity.create(
            userId,
            EconomyLedgerType.DAILY_CLAIM,
            amount,
            balanceAfter,
            "DAILY",
            tradingDay.toString(),
            dailyClaimIdempotencyKey(userId, tradingDay),
            null,
            null,
            null,
            policySnapshot.policyVersion(),
            policySnapshot.policySource()));

    int consecutiveDays = calculateConsecutiveClaimDays(userId, tradingDay);
    appendDailyClaimedEvent(userId, tradingDay, amount, consecutiveDays);
    appendBugBalanceChangedEvent(
        userId,
        amount,
        balanceAfter,
        EconomyLedgerType.DAILY_CLAIM.name(),
        tradingDay.toString(),
        dailyClaimIdempotencyKey(userId, tradingDay));

    return DailyClaimResult.claimed(amount, balanceAfter);
  }

  private int calculateConsecutiveClaimDays(String userId, LocalDate tradingDay) {
    List<EconomyDailyClaimJpaEntity> recentClaims =
        dailyClaimRepository.findTop30ByUserIdAndTradingDayLessThanEqualOrderByTradingDayDesc(
            userId, tradingDay);

    int consecutiveDays = 0;
    LocalDate expectedDay = tradingDay;
    for (EconomyDailyClaimJpaEntity claim : recentClaims) {
      if (!expectedDay.equals(claim.getTradingDay())) {
        break;
      }
      consecutiveDays++;
      expectedDay = expectedDay.minusDays(1);
    }
    return Math.max(1, consecutiveDays);
  }

  /**
   * Emits wallet-balance outbox events for downstream achievement/SSE consumers.
   *
   * <p>Event key is derived from write idempotency key to keep at-least-once relay idempotent.
   */
  private void appendBugBalanceChangedEvent(
      String userId,
      long delta,
      long balanceAfter,
      String reason,
      String refId,
      String operationKey) {
    outboxEventStore.append(
        new OutboxEventCommand(
            EconomyConstants.Outbox.AGGREGATE_TYPE_WALLET,
            userId,
            EconomyConstants.Outbox.EVENT_BUG_BALANCE_CHANGED,
            "economy:wallet-balance-changed:"
                + normalizeOutboxToken(operationKey),
            new BugBalanceChangedEventPayload(userId, delta, balanceAfter, reason, refId),
            Instant.now()));
  }

  private void appendDailyClaimedEvent(
      String userId, LocalDate tradingDay, int amount, int consecutiveDays) {
    outboxEventStore.append(
        new OutboxEventCommand(
            EconomyConstants.Outbox.AGGREGATE_TYPE_WALLET,
            userId,
            EconomyConstants.Outbox.EVENT_DAILY_CLAIMED,
            "economy:daily-claimed:" + userId + ":" + tradingDay,
            new DailyClaimedEventPayload(userId, amount, tradingDay, consecutiveDays),
            Instant.now()));
  }

  private String normalizeOutboxToken(String value) {
    if (!StringUtils.hasText(value)) {
      return "unknown";
    }
    return value.trim().replaceAll("[^a-zA-Z0-9:_-]", "_");
  }

  private EconomyWalletJpaEntity loadWalletForUpdateOrCreate(String userId) {
    Optional<EconomyWalletJpaEntity> existing = walletRepository.findByUserIdForUpdate(userId);
    if (existing.isPresent()) {
      return existing.get();
    }

    // Bootstrap from auth snapshot only when wallet row does not exist yet.
    EconomyWalletJpaEntity candidate =
        EconomyWalletJpaEntity.create(userId, walletBootstrapPort.initialBalanceFor(userId));
    try {
      walletRepository.saveAndFlush(candidate);
      return candidate;
    } catch (DataIntegrityViolationException ex) {
      return walletRepository.findByUserIdForUpdate(userId).orElseThrow(() -> ex);
    }
  }

  private void validateDeductAmount(int amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("Deduct amount must be positive");
    }
  }

  private void validateCreditAmount(int amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("Credit amount must be positive");
    }
  }

  private int resolveLedgerLimit(Integer limit) {
    if (limit == null) {
      return 20;
    }
    return Math.min(Math.max(limit, 1), 50);
  }

  private EconomyLedgerType resolveReasonFilter(String reason) {
    if (!StringUtils.hasText(reason)) {
      return null;
    }

    return switch (reason.trim().toUpperCase(Locale.ROOT)) {
      case "VOTE" -> EconomyLedgerType.ARENA_VOTE_COST;
      case "DAILY" -> EconomyLedgerType.DAILY_CLAIM;
      case "GAME" -> EconomyLedgerType.GAME_REWARD;
      case "BET" -> EconomyLedgerType.BET;
      case "BET_WIN" -> EconomyLedgerType.BET_WIN;
      case "HOUSE_STAKE" -> EconomyLedgerType.HOUSE_STAKE;
      case "HOUSE_WIN" -> EconomyLedgerType.HOUSE_WIN;
      case "MOON_DOOM_BONUS" -> EconomyLedgerType.MOON_DOOM_BONUS;
      case "FORCE_SETTLE_REFUND" -> EconomyLedgerType.FORCE_SETTLE_REFUND;
      case "RAKE" -> EconomyLedgerType.RAKE;
      default -> throw EconomyExceptions.invalidReason(EconomyConstants.Message.INVALID_REASON);
    };
  }

  private LedgerItemView toLedgerItemView(EconomyLedgerJpaEntity ledger) {
    return new LedgerItemView(
        ledger.getLedgerId(),
        ledger.getDelta(),
        ledger.getBalanceAfter(),
        toApiReason(ledger.getEntryType()),
        ledger.getRefId(),
        ledger.getRefType(),
        ledger.getCreatedAt());
  }

  private String toApiReason(EconomyLedgerType entryType) {
    return switch (entryType) {
      case ARENA_VOTE_COST -> "VOTE";
      case DAILY_CLAIM -> "DAILY";
      case GAME_REWARD -> "GAME";
      default -> entryType.name();
    };
  }

  private LocalDate tradingDayUtc() {
    return LocalDate.now(ZoneOffset.UTC);
  }

  private String dailyClaimIdempotencyKey(String userId, LocalDate tradingDay) {
    return "daily:"
        + userId
        + ":"
        + DAILY_CLAIM_KEY_DATE.format(tradingDay);
  }

  public record WalletView(
      String userId,
      long balance,
      long totalEarned,
      long totalSpent,
      boolean dailyClaimed,
      int dailyAmount,
      int voteCost) {}

  public record LedgerPageView(List<LedgerItemView> items, String nextCursor, boolean hasMore) {}

  public record LedgerItemView(
      String ledgerId,
      long delta,
      long balanceAfter,
      String reason,
      String refId,
      String refType,
      Instant createdAt) {}

  public record DailyClaimResult(
      boolean claimed, int amount, long balanceAfter, boolean alreadyClaimed) {

    static DailyClaimResult claimed(int amount, long balanceAfter) {
      return new DailyClaimResult(true, amount, balanceAfter, false);
    }

    static DailyClaimResult alreadyClaimed(long balanceAfter) {
      return new DailyClaimResult(false, 0, balanceAfter, true);
    }
  }

  public record DeductCommand(
      String userId,
      int amount,
      String refType,
      String refId,
      String idempotencyKey,
      DeductPolicySnapshot policySnapshot) {}

  public record DeductPolicySnapshot(
      Integer bugCost,
      BigDecimal rakeRate,
      Integer minBet,
      String policyVersion,
      String policySource) {}

  public record CreditCommand(
      String userId,
      int amount,
      String refType,
      String refId,
      String idempotencyKey,
      String policyVersion,
      String policySource) {}

  private record BugBalanceChangedEventPayload(
      String userId, long delta, long balanceAfter, String reason, String refId) {}

  private record DailyClaimedEventPayload(
      String userId, int amount, LocalDate claimDate, int consecutiveDays) {}

  public static class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String userId, long currentBalance, int requiredAmount) {
      super(
          "Insufficient balance: userId="
              + userId
              + ", balance="
              + currentBalance
              + ", required="
              + requiredAmount);
    }
  }
}
