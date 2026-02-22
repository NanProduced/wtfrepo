package com.wtfrepo.backend.economy.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.economy.application.policy.EconomyPolicyPort;
import com.wtfrepo.backend.economy.application.policy.EconomyPolicySnapshot;
import com.wtfrepo.backend.economy.application.support.EconomyConstants;
import com.wtfrepo.backend.economy.application.support.EconomyExceptions;
import com.wtfrepo.backend.economy.domain.EconomyLedgerType;
import com.wtfrepo.backend.economy.domain.GameTypeStatus;
import com.wtfrepo.backend.economy.domain.GameValidationStatus;
import com.wtfrepo.backend.economy.infra.persistence.entity.GameSessionJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.entity.GameTypeConfigJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.repository.GameSessionJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.GameTypeConfigJpaRepository;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Economy game service for game type discovery and score settlement.
 *
 * <p>Current implementation focuses on M03 Core: idempotent score intake, basic anti-cheat
 * validation, and wallet crediting through the economy single source of truth.
 */
@Service
public class EconomyGameService {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final int FALLBACK_DIVISOR = 2;

  private final GameTypeConfigJpaRepository gameTypeConfigRepository;
  private final GameSessionJpaRepository gameSessionRepository;
  private final EconomyWalletService economyWalletService;
  private final EconomyPolicyPort economyPolicyPort;
  private final ObjectMapper objectMapper;
  private final OutboxEventStore outboxEventStore;

  public EconomyGameService(
      GameTypeConfigJpaRepository gameTypeConfigRepository,
      GameSessionJpaRepository gameSessionRepository,
      EconomyWalletService economyWalletService,
      EconomyPolicyPort economyPolicyPort,
      ObjectMapper objectMapper,
      OutboxEventStore outboxEventStore) {
    this.gameTypeConfigRepository = gameTypeConfigRepository;
    this.gameSessionRepository = gameSessionRepository;
    this.economyWalletService = economyWalletService;
    this.economyPolicyPort = economyPolicyPort;
    this.objectMapper = objectMapper;
    this.outboxEventStore = outboxEventStore;
  }

  @Transactional(readOnly = true)
  public GameTypesView listGameTypes(String userId) {
    EconomyPolicySnapshot policySnapshot = economyPolicyPort.currentPolicySnapshot();
    List<GameTypeView> games = loadConfiguredGameTypes().stream().map(this::toGameTypeView).toList();

    Long dailyGameBugEarned = null;
    if (StringUtils.hasText(userId)) {
      dailyGameBugEarned = dailyBugEarned(userId);
    }

    return new GameTypesView(games, policySnapshot.dailyGameBugCap(), dailyGameBugEarned);
  }

  @Transactional
  public SubmitScoreResult submitScore(
      String userId, String idempotencyKey, SubmitScoreCommand command) {
    String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey, userId, command);

    Optional<GameSessionJpaEntity> existingSession =
        gameSessionRepository.findByIdempotencyKey(normalizedIdempotencyKey);
    if (existingSession.isPresent()) {
      return replayExisting(userId, existingSession.get());
    }

    if (command.score() < 0 || command.durationMs() <= 0) {
      throw EconomyExceptions.gameInvalidScore(EconomyConstants.Message.GAME_INVALID_SCORE);
    }

    GameTypeConfigView gameTypeConfig = findActiveGameType(command.gameType());
    Instant dayStart = dayStartUtc();
    Instant dayEnd = dayEndUtc();

    long dailyPlayCountBefore =
        gameSessionRepository.countByUserIdAndGameTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            userId, gameTypeConfig.gameType(), dayStart, dayEnd);
    if (dailyPlayCountBefore >= gameTypeConfig.dailyPlayLimit()) {
      throw EconomyExceptions.gameDailyLimitExceeded(
          EconomyConstants.Message.GAME_DAILY_LIMIT_EXCEEDED);
    }

    ValidationResult validationResult = validateScore(gameTypeConfig, command.score(), command.durationMs());

    EconomyPolicySnapshot policySnapshot = economyPolicyPort.currentPolicySnapshot();
    long dailyBugEarnedBefore = dailyBugEarned(userId);

    int bugEarned = 0;
    long balanceAfter = economyWalletService.currentBalance(userId);
    if (validationResult.status() == GameValidationStatus.VALID) {
      int rawBugEarned = calculateBugEarned(gameTypeConfig, command.score());
      long remainingBugCap = policySnapshot.dailyGameBugCap() - dailyBugEarnedBefore;
      if (remainingBugCap <= 0) {
        throw EconomyExceptions.gameDailyBugCapReached(
            EconomyConstants.Message.GAME_DAILY_BUG_CAP_REACHED);
      }

      bugEarned = (int) Math.min(rawBugEarned, remainingBugCap);
      if (bugEarned > 0) {
        balanceAfter =
            economyWalletService.credit(
                new EconomyWalletService.CreditCommand(
                    userId,
                    bugEarned,
                    EconomyConstants.RefType.GAME,
                    gameTypeConfig.gameType(),
                    "game:" + normalizedIdempotencyKey,
                    policySnapshot.policyVersion(),
                    policySnapshot.policySource()),
                EconomyLedgerType.GAME_REWARD);
      }
    }

    GameSessionJpaEntity saved =
        gameSessionRepository.save(
            GameSessionJpaEntity.create(
                userId,
                gameTypeConfig.gameType(),
                command.score(),
                command.durationMs(),
                bugEarned,
                balanceAfter,
                command.clientSessionId(),
                serializeJson(command.extraData()),
                validationResult.status(),
                validationResult.reason(),
                normalizedIdempotencyKey));
    appendGameSessionCompletedEvent(saved);

    long dailyPlayCountAfter = dailyPlayCountBefore + 1;
    long dailyBugEarnedAfter = dailyBugEarnedBefore + bugEarned;
    return toSubmitScoreResult(saved, dailyPlayCountAfter, dailyBugEarnedAfter, policySnapshot);
  }

  private SubmitScoreResult replayExisting(String userId, GameSessionJpaEntity existingSession) {
    if (!Objects.equals(existingSession.getUserId(), userId)) {
      throw EconomyExceptions.gameInvalidScore(EconomyConstants.Message.GAME_INVALID_SCORE);
    }

    EconomyPolicySnapshot policySnapshot = economyPolicyPort.currentPolicySnapshot();
    Instant dayStart = dayStartUtc();
    Instant dayEnd = dayEndUtc();
    long dailyPlayCount =
        gameSessionRepository.countByUserIdAndGameTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            userId, existingSession.getGameType(), dayStart, dayEnd);
    long dailyBugEarned = dailyBugEarned(userId);
    return toSubmitScoreResult(existingSession, dailyPlayCount, dailyBugEarned, policySnapshot);
  }

  private SubmitScoreResult toSubmitScoreResult(
      GameSessionJpaEntity session,
      long dailyPlayCount,
      long dailyBugEarned,
      EconomyPolicySnapshot policySnapshot) {
    return new SubmitScoreResult(
        session.getId(),
        session.getGameType(),
        session.getScore(),
        session.getBugEarned(),
        session.getBalanceAfter(),
        session.getValidationStatus().name(),
        session.getValidationReason(),
        (int) dailyPlayCount,
        (int) dailyBugEarned,
        policySnapshot.dailyGameBugCap());
  }

  private List<GameTypeConfigView> loadConfiguredGameTypes() {
    List<GameTypeConfigJpaEntity> rows = gameTypeConfigRepository.findAllByOrderBySortOrderAscGameTypeKeyAsc();
    if (!rows.isEmpty()) {
      return rows.stream().map(this::toConfigView).toList();
    }

    // JPA-first stage: use contract baseline defaults until DB seed is reviewed/finalized.
    return List.of(
        new GameTypeConfigView(
            "FLAPPY_DUKE",
            "Flappy Duke",
            "Flappy Duke",
            "Control Duke and fly through obstacles.",
            "🪫",
            GameTypeStatus.ACTIVE,
            "SCORE_EQUALS_BUG",
            "{}",
            null,
            1000,
            50,
            "{}"),
        new GameTypeConfigView(
            "BUG_CATCHER",
            "Bug Catcher",
            "Bug Catcher",
            "Catch as many bugs as possible within limited time.",
            "🐞",
            GameTypeStatus.COMING_SOON,
            "SCORE_DIVIDED_BY_N",
            "{\"divisor\":2}",
            null,
            1000,
            30,
            "{}"));
  }

  private GameTypeConfigView toConfigView(GameTypeConfigJpaEntity entity) {
    return new GameTypeConfigView(
        entity.getGameTypeKey(),
        entity.getNameZh(),
        entity.getNameEn(),
        entity.getDescription(),
        entity.getIcon(),
        entity.getStatus(),
        entity.getBugFormula(),
        entity.getBugFormulaParams(),
        entity.getMaxScorePerSecond(),
        entity.getMinDurationMs(),
        Optional.ofNullable(entity.getDailyPlayLimit()).orElse(50),
        entity.getUiMeta());
  }

  private GameTypeView toGameTypeView(GameTypeConfigView config) {
    return new GameTypeView(
        config.gameType(),
        config.nameZh(),
        config.nameEn(),
        config.description(),
        config.icon(),
        config.status().name(),
        config.bugFormula(),
        config.dailyPlayLimit(),
        parseJsonObject(config.uiMetaJson()));
  }

  private GameTypeConfigView findActiveGameType(String gameType) {
    if (!StringUtils.hasText(gameType)) {
      throw EconomyExceptions.gameInvalidType(EconomyConstants.Message.GAME_INVALID_TYPE);
    }

    String normalizedGameType = gameType.trim().toUpperCase(Locale.ROOT);
    return loadConfiguredGameTypes().stream()
        .filter(config -> normalizedGameType.equals(config.gameType()))
        .filter(config -> config.status() == GameTypeStatus.ACTIVE)
        .findFirst()
        .orElseThrow(() -> EconomyExceptions.gameInvalidType(EconomyConstants.Message.GAME_INVALID_TYPE));
  }

  private ValidationResult validateScore(GameTypeConfigView config, int score, int durationMs) {
    if (config.minDurationMs() != null && durationMs < config.minDurationMs()) {
      return new ValidationResult(GameValidationStatus.REJECTED, "duration_too_short");
    }

    if (config.maxScorePerSecond() != null) {
      BigDecimal scorePerSecond =
          BigDecimal.valueOf(score)
              .multiply(BigDecimal.valueOf(1000L))
              .divide(BigDecimal.valueOf(durationMs), 4, RoundingMode.HALF_UP);
      if (scorePerSecond.compareTo(config.maxScorePerSecond()) > 0) {
        return new ValidationResult(GameValidationStatus.REJECTED, "score_duration_mismatch");
      }
    }

    return new ValidationResult(GameValidationStatus.VALID, null);
  }

  private int calculateBugEarned(GameTypeConfigView config, int score) {
    if ("SCORE_DIVIDED_BY_N".equalsIgnoreCase(config.bugFormula())) {
      int divisor = resolveDivisor(config.bugFormulaParamsJson());
      return Math.max(score / divisor, 0);
    }
    return Math.max(score, 0);
  }

  private int resolveDivisor(String bugFormulaParamsJson) {
    Map<String, Object> params = parseJsonObject(bugFormulaParamsJson);
    Object divisorValue = params.get("divisor");
    if (divisorValue instanceof Number number && number.intValue() > 0) {
      return number.intValue();
    }
    return FALLBACK_DIVISOR;
  }

  /** Emits completion event for achievement/review consumers after game session write commits. */
  private void appendGameSessionCompletedEvent(GameSessionJpaEntity session) {
    outboxEventStore.append(
        new OutboxEventCommand(
            EconomyConstants.Outbox.AGGREGATE_TYPE_GAME_SESSION,
            session.getId(),
            EconomyConstants.Outbox.EVENT_GAME_SESSION_COMPLETED,
            "economy:game-session-completed:" + session.getId(),
            new GameSessionCompletedEventPayload(
                session.getUserId(),
                session.getGameType(),
                session.getScore(),
                session.getBugEarned(),
                session.getValidationStatus().name()),
            session.getCreatedAt()));
  }

  private long dailyBugEarned(String userId) {
    return gameSessionRepository.sumDailyBugEarned(
        userId, GameValidationStatus.VALID, dayStartUtc(), dayEndUtc());
  }

  private String normalizeIdempotencyKey(
      String idempotencyKey, String userId, SubmitScoreCommand command) {
    if (StringUtils.hasText(idempotencyKey)) {
      return idempotencyKey.trim();
    }

    return "game:"
        + userId
        + ":"
        + command.gameType().trim().toUpperCase(Locale.ROOT)
        + ":"
        + command.clientSessionId().trim();
  }

  private String serializeJson(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) {
      return null;
    }

    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw EconomyExceptions.gameInvalidScore(EconomyConstants.Message.GAME_INVALID_SCORE);
    }
  }

  private Map<String, Object> parseJsonObject(String rawJson) {
    if (!StringUtils.hasText(rawJson)) {
      return Map.of();
    }

    try {
      return objectMapper.readValue(rawJson, MAP_TYPE);
    } catch (JsonProcessingException ex) {
      return Map.of();
    }
  }

  private Instant dayStartUtc() {
    return LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  private Instant dayEndUtc() {
    return LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  public record GameTypesView(List<GameTypeView> games, int dailyGameBugCap, Long dailyGameBugEarned) {}

  public record GameTypeView(
      String gameType,
      String nameZh,
      String nameEn,
      String description,
      String icon,
      String status,
      String bugFormula,
      int dailyPlayLimit,
      Map<String, Object> uiMeta) {}

  public record SubmitScoreCommand(
      String gameType,
      int score,
      int durationMs,
      String clientSessionId,
      Map<String, Object> extraData) {}

  public record SubmitScoreResult(
      String sessionId,
      String gameType,
      int score,
      int bugEarned,
      long balanceAfter,
      String validationStatus,
      String validationReason,
      int dailyGameCount,
      int dailyGameBugEarned,
      int dailyGameBugCap) {}

  private record GameSessionCompletedEventPayload(
      String userId, String gameType, int score, int bugEarned, String validationStatus) {}

  private record ValidationResult(GameValidationStatus status, String reason) {}

  private record GameTypeConfigView(
      String gameType,
      String nameZh,
      String nameEn,
      String description,
      String icon,
      GameTypeStatus status,
      String bugFormula,
      String bugFormulaParamsJson,
      BigDecimal maxScorePerSecond,
      Integer minDurationMs,
      int dailyPlayLimit,
      String uiMetaJson) {}
}
