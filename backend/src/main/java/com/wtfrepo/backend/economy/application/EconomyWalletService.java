package com.wtfrepo.backend.economy.application;

import com.wtfrepo.backend.economy.application.policy.EconomyPolicyPort;
import com.wtfrepo.backend.economy.application.policy.EconomyPolicySnapshot;
import com.wtfrepo.backend.economy.domain.EconomyLedgerType;
import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyDailyClaimJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyLedgerJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyWalletJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyDailyClaimJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyLedgerJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.EconomyWalletJpaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Economy wallet S0 service for balance, vote deduction and daily claim. */
@Service
public class EconomyWalletService {

  private static final DateTimeFormatter DAILY_CLAIM_KEY_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final EconomyWalletJpaRepository walletRepository;
  private final EconomyDailyClaimJpaRepository dailyClaimRepository;
  private final EconomyLedgerJpaRepository ledgerRepository;
  private final EconomyWalletBootstrapPort walletBootstrapPort;
  private final EconomyPolicyPort economyPolicyPort;

  public EconomyWalletService(
      EconomyWalletJpaRepository walletRepository,
      EconomyDailyClaimJpaRepository dailyClaimRepository,
      EconomyLedgerJpaRepository ledgerRepository,
      EconomyWalletBootstrapPort walletBootstrapPort,
      EconomyPolicyPort economyPolicyPort) {
    this.walletRepository = walletRepository;
    this.dailyClaimRepository = dailyClaimRepository;
    this.ledgerRepository = ledgerRepository;
    this.walletBootstrapPort = walletBootstrapPort;
    this.economyPolicyPort = economyPolicyPort;
  }

  @Transactional(readOnly = true)
  public WalletView getWallet(String userId) {
    long balance = currentBalance(userId);
    EconomyPolicySnapshot policySnapshot = economyPolicyPort.currentPolicySnapshot();
    boolean dailyClaimed =
        dailyClaimRepository
            .findByUserIdAndTradingDay(userId, tradingDayUtc())
            .map(claim -> true)
            .orElse(false);
    return new WalletView(userId, balance, dailyClaimed, policySnapshot.dailyClaimAmount());
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
            "DAILY_CLAIM",
            tradingDay.toString(),
            dailyClaimIdempotencyKey(userId, tradingDay),
            null,
            null,
            null,
            policySnapshot.policyVersion(),
            policySnapshot.policySource()));

    return DailyClaimResult.claimed(amount, balanceAfter);
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

  private LocalDate tradingDayUtc() {
    return LocalDate.now(ZoneOffset.UTC);
  }

  private String dailyClaimIdempotencyKey(String userId, LocalDate tradingDay) {
    return "daily:"
        + userId
        + ":"
        + DAILY_CLAIM_KEY_DATE.format(tradingDay);
  }

  public record WalletView(String userId, long balance, boolean dailyClaimed, int dailyAmount) {}

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
