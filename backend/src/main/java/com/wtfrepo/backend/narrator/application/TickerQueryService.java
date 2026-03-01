package com.wtfrepo.backend.narrator.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.narrator.application.model.NarratorModels;
import com.wtfrepo.backend.narrator.application.support.NarratorConstants;
import com.wtfrepo.backend.narrator.application.support.NarratorExceptions;
import com.wtfrepo.backend.shared.outbox.OutboxEventJpaEntity;
import com.wtfrepo.backend.shared.outbox.OutboxEventJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Read service for ticker recent backfill endpoint.
 *
 * <p>MVP stage uses outbox rows as temporary read source until dedicated ticker read-model table is
 * introduced.
 */
@Service
public class TickerQueryService {

  private static final int DEFAULT_LIMIT = 10;
  private static final int MAX_LIMIT = 30;

  private static final String PRIORITY_P0 = "P0_CRITICAL";
  private static final String PRIORITY_P1 = "P1_ACTION";
  private static final String PRIORITY_P2 = "P2_AMBIENT";

  private static final Duration DEFAULT_TTL = Duration.ofHours(24);

  private static final Map<String, String> EVENT_KEY_MAP =
      Map.of(
          "EloUpdatedEvent", "ticker.elo_delta_major",
          "DuelSettledEvent", "ticker.duel_settled",
          "DailySnapshotCreatedEvent", "ticker.daily_settlement",
          "SpecimenActivatedEvent", "ticker.specimen_new_active",
          "SpecimenRankChangedEvent", "ticker.rank_surge",
          "TopRoastUpdatedEvent", "ticker.top_roast_changed",
          "BettingPoolChangedEvent", "ticker.betting_pool_hot",
          "SystemAnnouncementEvent", "ticker.system_announcement");

  private static final List<String> SUPPORTED_SOURCE_EVENT_TYPES =
      List.copyOf(EVENT_KEY_MAP.keySet());

  private final OutboxEventJpaRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  public TickerQueryService(
      OutboxEventJpaRepository outboxEventRepository, ObjectMapper objectMapper) {
    this.outboxEventRepository = outboxEventRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public NarratorModels.TickerRecentPage listRecent(String cursor, Integer limit) {
    int resolvedLimit = resolveLimit(limit);
    CursorAnchor cursorAnchor = resolveCursorAnchor(cursor);

    List<OutboxEventJpaEntity> fetched =
        outboxEventRepository.findTickerRecentCandidates(
            SUPPORTED_SOURCE_EVENT_TYPES,
            cursorAnchor == null ? null : cursorAnchor.createdAt(),
            cursorAnchor == null ? null : cursorAnchor.eventId(),
            PageRequest.of(0, resolvedLimit + 1));

    boolean hasMore = fetched.size() > resolvedLimit;
    List<OutboxEventJpaEntity> pageWindow = hasMore ? fetched.subList(0, resolvedLimit) : fetched;

    List<NarratorModels.TickerRecentItem> items = pageWindow.stream().map(this::toItem).toList();
    String nextCursor =
        hasMore && !pageWindow.isEmpty() ? pageWindow.get(pageWindow.size() - 1).getEventId() : null;
    return new NarratorModels.TickerRecentPage(items, nextCursor, hasMore);
  }

  private int resolveLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private CursorAnchor resolveCursorAnchor(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }
    String normalizedCursor = cursor.trim();
    if (normalizedCursor.length() > 128) {
      throw NarratorExceptions.invalidCursor(NarratorConstants.Message.INVALID_CURSOR);
    }

    OutboxEventJpaEntity anchor =
        outboxEventRepository
            .findById(normalizedCursor)
            .orElseThrow(
                () -> NarratorExceptions.invalidCursor(NarratorConstants.Message.INVALID_CURSOR));
    if (!SUPPORTED_SOURCE_EVENT_TYPES.contains(anchor.getEventType())) {
      throw NarratorExceptions.invalidCursor(NarratorConstants.Message.INVALID_CURSOR);
    }
    return new CursorAnchor(anchor.getCreatedAt(), anchor.getEventId());
  }

  private NarratorModels.TickerRecentItem toItem(OutboxEventJpaEntity event) {
    String eventType = normalizeTickerEventType(event.getEventType());
    JsonNode payload = readPayload(event.getPayloadJson());
    String actionUrl = resolveActionUrl(payload, event.getAggregateId());
    String text = resolveText(eventType, payload, event.getAggregateId());
    String priority = resolvePriority(eventType);
    Instant occurredAt = event.getOccurredAt();
    Instant expiresAt = occurredAt == null ? null : occurredAt.plus(DEFAULT_TTL);
    return new NarratorModels.TickerRecentItem(
        event.getEventId(), eventType, text, priority, actionUrl, occurredAt, expiresAt);
  }

  private JsonNode readPayload(String payloadJson) {
    if (!StringUtils.hasText(payloadJson)) {
      return objectMapper.createObjectNode();
    }
    try {
      return objectMapper.readTree(payloadJson);
    } catch (Exception ex) {
      return objectMapper.createObjectNode();
    }
  }

  private String normalizeTickerEventType(String sourceEventType) {
    if (!StringUtils.hasText(sourceEventType)) {
      return "ticker.unknown";
    }
    String normalized = sourceEventType.trim();
    if (normalized.startsWith("ticker.")) {
      return normalized;
    }
    return EVENT_KEY_MAP.getOrDefault(normalized, "ticker.unknown");
  }

  private String resolvePriority(String eventType) {
    return switch (eventType) {
      case "ticker.system_announcement" -> PRIORITY_P0;
      case "ticker.daily_settlement", "ticker.rank_surge", "ticker.betting_pool_hot" -> PRIORITY_P1;
      default -> PRIORITY_P2;
    };
  }

  private String resolveText(String eventType, JsonNode payload, String aggregateId) {
    String payloadText = readText(payload, "text");
    if (StringUtils.hasText(payloadText)) {
      return payloadText;
    }

    return switch (eventType) {
      case "ticker.elo_delta_major" -> {
        int before = payload.path("eloBefore").asInt(0);
        int after = payload.path("eloAfter").asInt(before);
        int delta = after - before;
        yield "标本 " + resolveSpecimenId(payload, aggregateId) + " Elo 变动 " + signed(delta);
      }
      case "ticker.daily_settlement" ->
          "每日结算完成（" + fallback(readText(payload, "date"), "unknown-date") + "）";
      case "ticker.specimen_new_active" -> "新标本已激活：" + resolveSpecimenId(payload, aggregateId);
      case "ticker.top_roast_changed" -> "热评更新：" + resolveSpecimenId(payload, aggregateId);
      case "ticker.rank_surge" -> "排名异动：" + resolveSpecimenId(payload, aggregateId);
      case "ticker.duel_settled" -> "对决已结算：" + fallback(readText(payload, "battleId"), "unknown");
      case "ticker.betting_pool_hot" -> "投注池升温：" + fallback(readText(payload, "battleId"), "unknown");
      case "ticker.system_announcement" -> "系统公告更新";
      default -> "市场动态更新";
    };
  }

  private String resolveActionUrl(JsonNode payload, String aggregateId) {
    String actionUrl = readText(payload, "actionUrl");
    if (StringUtils.hasText(actionUrl)) {
      return actionUrl;
    }

    String specimenId = resolveSpecimenId(payload, aggregateId);
    if (StringUtils.hasText(specimenId)) {
      String commentId = readText(payload, "commentId");
      if (StringUtils.hasText(commentId)) {
        return "/specimen/" + specimenId + "#comment-" + commentId;
      }
      return "/specimen/" + specimenId;
    }
    return null;
  }

  private String resolveSpecimenId(JsonNode payload, String aggregateId) {
    String specimenId = readText(payload, "specimenId");
    if (StringUtils.hasText(specimenId)) {
      return specimenId;
    }
    if (StringUtils.hasText(aggregateId) && !"SYSTEM".equalsIgnoreCase(aggregateId)) {
      return aggregateId.trim();
    }
    return null;
  }

  private String readText(JsonNode payload, String fieldName) {
    JsonNode field = payload.path(fieldName);
    if (field.isMissingNode() || field.isNull()) {
      return null;
    }
    String value = field.asText();
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String signed(int value) {
    return value > 0 ? "+" + value : String.valueOf(value);
  }

  private String fallback(String value, String defaultValue) {
    return StringUtils.hasText(value) ? value : defaultValue;
  }

  private record CursorAnchor(Instant createdAt, String eventId) {}
}
