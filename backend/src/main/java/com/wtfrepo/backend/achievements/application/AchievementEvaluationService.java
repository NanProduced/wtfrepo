package com.wtfrepo.backend.achievements.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wtfrepo.backend.achievements.infra.persistence.entity.AchievementInboxEventJpaEntity;
import com.wtfrepo.backend.achievements.infra.persistence.entity.UserAchievementJpaEntity;
import com.wtfrepo.backend.achievements.infra.persistence.repository.AchievementInboxEventJpaRepository;
import com.wtfrepo.backend.achievements.infra.persistence.repository.UserAchievementJpaRepository;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** MVP evaluator/unlock engine for achievements triggered from inbox events. */
@Service
public class AchievementEvaluationService {

  private static final Logger log = LoggerFactory.getLogger(AchievementEvaluationService.class);

  private static final String EVENT_NARRATOR_PREFERENCE_CHANGED = "NarratorPreferenceChangedEvent";
  private static final String EVENT_VOTE_COMPLETED = "VoteCompletedEvent";
  private static final String EVENT_TICKER_ITEM_CLICKED = "TickerItemClickedEvent";
  private static final String OUTBOX_EVENT_ACHIEVEMENT_UNLOCKED = "AchievementUnlockedEvent";
  private static final String OUTBOX_AGGREGATE_TYPE = "USER_ACHIEVEMENT";

  private static final int NARRATOR_FULL_STREAK_TARGET_DAYS = 3;

  private final AchievementCatalog achievementCatalog;
  private final UserAchievementJpaRepository userAchievementRepository;
  private final AchievementInboxEventJpaRepository inboxEventRepository;
  private final ArenaVoteLookupPort arenaVoteLookupPort;
  private final AchievementsPolicyProperties policyProperties;
  private final OutboxEventStore outboxEventStore;
  private final ObjectMapper objectMapper;

  public AchievementEvaluationService(
      AchievementCatalog achievementCatalog,
      UserAchievementJpaRepository userAchievementRepository,
      AchievementInboxEventJpaRepository inboxEventRepository,
      ArenaVoteLookupPort arenaVoteLookupPort,
      AchievementsPolicyProperties policyProperties,
      OutboxEventStore outboxEventStore,
      ObjectMapper objectMapper) {
    this.achievementCatalog = achievementCatalog;
    this.userAchievementRepository = userAchievementRepository;
    this.inboxEventRepository = inboxEventRepository;
    this.arenaVoteLookupPort = arenaVoteLookupPort;
    this.policyProperties = policyProperties;
    this.outboxEventStore = outboxEventStore;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void evaluateInboundEvent(AchievementInboxEventJpaEntity inboxEvent) {
    if (inboxEvent == null || !StringUtils.hasText(inboxEvent.getEventType())) {
      return;
    }
    String eventType = inboxEvent.getEventType().trim();
    if (EVENT_NARRATOR_PREFERENCE_CHANGED.equals(eventType)) {
      evaluateNarratorPreferenceChanged(inboxEvent);
      return;
    }
    if (EVENT_VOTE_COMPLETED.equals(eventType)) {
      evaluateVoteCompleted(inboxEvent);
      return;
    }
    if (EVENT_TICKER_ITEM_CLICKED.equals(eventType)) {
      log.debug(
          "achievement_eval_defer eventType={} reason=wait_vote_completion eventId={}",
          eventType,
          inboxEvent.getEventId());
    }
  }

  private void evaluateNarratorPreferenceChanged(AchievementInboxEventJpaEntity inboxEvent) {
    JsonNode payload = parsePayload(inboxEvent);
    String userId = resolveUserId(payload, inboxEvent);
    if (!StringUtils.hasText(userId)) {
      log.debug(
          "achievement_eval_skip eventType={} eventId={} reason=missing_user_id",
          inboxEvent.getEventType(),
          inboxEvent.getEventId());
      return;
    }

    String oldMode = readUpper(payload, "oldMode");
    String newMode = readUpper(payload, "newMode");
    Instant resolvedEventTime = resolveEventTime(inboxEvent);

    if (StringUtils.hasText(oldMode) && StringUtils.hasText(newMode) && !oldMode.equals(newMode)) {
      unlockIfNeeded(
          userId,
          resolveDefinition(AchievementCatalog.CODE_NARRATOR_MODE_SWITCHER),
          inboxEvent,
          resolvedEventTime,
          payload);
    }

    if ("FULL".equals(newMode)
        && hasFullModeStreak(userId, resolvedEventTime, NARRATOR_FULL_STREAK_TARGET_DAYS)) {
      unlockIfNeeded(
          userId,
          resolveDefinition(AchievementCatalog.CODE_CHAOS_FULL_ON),
          inboxEvent,
          resolvedEventTime,
          payload);
    }
  }

  private boolean hasFullModeStreak(String userId, Instant anchor, int targetDays) {
    LocalDate anchorDate = anchor.atZone(ZoneOffset.UTC).toLocalDate();
    List<AchievementInboxEventJpaEntity> history =
        inboxEventRepository.findByEventTypeAndAggregateIdOrderByReceivedAtDesc(
            EVENT_NARRATOR_PREFERENCE_CHANGED,
            userId,
            PageRequest.of(0, Math.max(1, policyProperties.getNarratorHistoryLookbackSize())));

    Set<LocalDate> fullDays = new HashSet<>();
    for (AchievementInboxEventJpaEntity item : history) {
      JsonNode payload = parsePayload(item);
      if (!"FULL".equals(readUpper(payload, "newMode"))) {
        continue;
      }
      Instant time = resolveEventTime(item);
      LocalDate day = time.atZone(ZoneOffset.UTC).toLocalDate();
      if (!day.isAfter(anchorDate)) {
        fullDays.add(day);
      }
    }

    LocalDate cursor = anchorDate;
    int streak = 0;
    while (fullDays.contains(cursor)) {
      streak++;
      if (streak >= targetDays) {
        return true;
      }
      cursor = cursor.minusDays(1);
    }
    return false;
  }

  private void evaluateVoteCompleted(AchievementInboxEventJpaEntity inboxEvent) {
    JsonNode votePayload = parsePayload(inboxEvent);
    String orderId = readText(votePayload, "orderId");
    if (!StringUtils.hasText(orderId)) {
      log.debug(
          "achievement_eval_skip eventType={} eventId={} reason=missing_order_id",
          inboxEvent.getEventType(),
          inboxEvent.getEventId());
      return;
    }

    String userId = arenaVoteLookupPort.findVoterUserIdByOrderId(orderId).orElse(null);
    if (!StringUtils.hasText(userId)) {
      log.debug(
          "achievement_eval_skip eventType={} eventId={} reason=missing_vote_actor orderId={}",
          inboxEvent.getEventType(),
          inboxEvent.getEventId(),
          orderId);
      return;
    }

    String battleId = readText(votePayload, "battleId");
    Instant voteTime = resolveEventTime(inboxEvent);
    AchievementInboxEventJpaEntity matchedTickerClick =
        findCorrelatedTickerClick(userId, battleId, voteTime);
    if (matchedTickerClick == null) {
      return;
    }

    JsonNode unlockContext = buildTickerVoteContext(votePayload, matchedTickerClick);
    unlockIfNeeded(
        userId,
        resolveDefinition(AchievementCatalog.CODE_TICKER_SCALPER),
        inboxEvent,
        voteTime,
        unlockContext);
  }

  private AchievementInboxEventJpaEntity findCorrelatedTickerClick(
      String userId, String battleId, Instant voteTime) {
    List<AchievementInboxEventJpaEntity> recentClicks =
        inboxEventRepository.findByEventTypeAndAggregateIdOrderByReceivedAtDesc(
            EVENT_TICKER_ITEM_CLICKED,
            userId,
            PageRequest.of(0, Math.max(1, policyProperties.getTickerClickLookbackSize())));

    Duration window = normalizeTickerToVoteWindow(policyProperties.getTickerToVoteWindow());
    Instant windowStart = voteTime.minus(window);

    for (AchievementInboxEventJpaEntity click : recentClicks) {
      Instant clickTime = resolveEventTime(click);
      if (clickTime.isAfter(voteTime)) {
        continue;
      }
      if (clickTime.isBefore(windowStart)) {
        break;
      }
      JsonNode clickPayload = parsePayload(click);
      if (isArenaIntentClick(clickPayload, battleId)) {
        return click;
      }
    }
    return null;
  }

  private Duration normalizeTickerToVoteWindow(Duration configuredWindow) {
    if (configuredWindow == null || configuredWindow.isNegative() || configuredWindow.isZero()) {
      return Duration.ofMinutes(5);
    }
    return configuredWindow;
  }

  private boolean isArenaIntentClick(JsonNode clickPayload, String battleId) {
    String actionUrl = readText(clickPayload, "actionUrl");
    String clickedEventType = readText(clickPayload, "eventType");
    if (StringUtils.hasText(actionUrl)) {
      String normalized = actionUrl.toLowerCase(Locale.ROOT);
      if (normalized.contains("/arena")
          || normalized.contains("/duel")
          || normalized.contains("battleid=")
          || normalized.contains("battle_id=")) {
        return true;
      }
      if (StringUtils.hasText(battleId)
          && normalized.contains(battleId.trim().toLowerCase(Locale.ROOT))) {
        return true;
      }
    }

    return "ticker.duel_settled".equalsIgnoreCase(clickedEventType)
        || "ticker.betting_pool_hot".equalsIgnoreCase(clickedEventType);
  }

  private JsonNode buildTickerVoteContext(
      JsonNode votePayload, AchievementInboxEventJpaEntity matchedTickerClick) {
    ObjectNode context = objectMapper.createObjectNode();
    context.set("vote", votePayload == null ? objectMapper.createObjectNode() : votePayload);
    context.put("tickerClickEventId", matchedTickerClick.getEventId());
    context.put("tickerClickOccurredAt", resolveEventTime(matchedTickerClick).toString());
    context.set("tickerClickPayload", parsePayload(matchedTickerClick));
    return context;
  }

  private void unlockIfNeeded(
      String userId,
      AchievementCatalog.Definition definition,
      AchievementInboxEventJpaEntity sourceEvent,
      Instant unlockedAt,
      JsonNode contextPayload) {
    if (definition == null) {
      return;
    }
    if (userAchievementRepository.existsByUserIdAndAchievementCode(userId, definition.achievementCode())) {
      return;
    }

    String contextJson = serializeContext(contextPayload);
    UserAchievementJpaEntity unlockRecord =
        UserAchievementJpaEntity.create(
            userId,
            definition.achievementCode(),
            sourceEvent.getEventId(),
            contextJson,
            definition.rewardBug(),
            unlockedAt);
    try {
      userAchievementRepository.save(unlockRecord);
    } catch (DataIntegrityViolationException ex) {
      if (userAchievementRepository.existsByUserIdAndAchievementCode(userId, definition.achievementCode())) {
        return;
      }
      throw ex;
    }

    AchievementUnlockedPayload payload =
        new AchievementUnlockedPayload(
            userId,
            definition.achievementCode(),
            definition.displayName(),
            definition.description(),
            definition.iconUrl(),
            definition.tier(),
            definition.rewardBug(),
            unlockedAt);
    outboxEventStore.append(
        new OutboxEventCommand(
            OUTBOX_AGGREGATE_TYPE,
            userId,
            OUTBOX_EVENT_ACHIEVEMENT_UNLOCKED,
            "achievements:unlocked:" + definition.achievementCode() + ":" + userId,
            payload,
            unlockedAt));

    log.info(
        "achievement_unlocked userId={} achievementCode={} sourceEventId={}",
        userId,
        definition.achievementCode(),
        sourceEvent.getEventId());
  }

  private AchievementCatalog.Definition resolveDefinition(String achievementCode) {
    return achievementCatalog
        .findByCode(achievementCode)
        .orElseGet(
            () -> {
              log.warn("achievement_eval_definition_missing achievementCode={}", achievementCode);
              return null;
            });
  }

  private String resolveUserId(JsonNode payload, AchievementInboxEventJpaEntity event) {
    String payloadUserId = readText(payload, "userId");
    if (StringUtils.hasText(payloadUserId)) {
      return payloadUserId;
    }
    if (StringUtils.hasText(event.getAggregateId())) {
      return event.getAggregateId().trim();
    }
    return null;
  }

  private JsonNode parsePayload(AchievementInboxEventJpaEntity event) {
    if (event == null || !StringUtils.hasText(event.getPayloadJson())) {
      return objectMapper.createObjectNode();
    }
    try {
      return objectMapper.readTree(event.getPayloadJson());
    } catch (Exception ex) {
      log.warn(
          "achievement_eval_payload_parse_failed eventId={} eventType={}",
          event.getEventId(),
          event.getEventType(),
          ex);
      return objectMapper.createObjectNode();
    }
  }

  private String serializeContext(JsonNode payload) {
    try {
      return objectMapper.writeValueAsString(payload == null ? objectMapper.createObjectNode() : payload);
    } catch (Exception ex) {
      return "{}";
    }
  }

  private String readText(JsonNode payload, String field) {
    JsonNode value = payload.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    String text = value.asText();
    return StringUtils.hasText(text) ? text.trim() : null;
  }

  private String readUpper(JsonNode payload, String field) {
    String value = readText(payload, field);
    return StringUtils.hasText(value) ? value.toUpperCase(Locale.ROOT) : null;
  }

  private Instant resolveEventTime(AchievementInboxEventJpaEntity event) {
    if (event.getOccurredAt() != null) {
      return event.getOccurredAt();
    }
    if (event.getReceivedAt() != null) {
      return event.getReceivedAt();
    }
    return Instant.now();
  }

  private record AchievementUnlockedPayload(
      String userId,
      String achievementCode,
      String displayName,
      String description,
      String iconUrl,
      String tier,
      int rewardBug,
      Instant unlockedAt) {}
}
