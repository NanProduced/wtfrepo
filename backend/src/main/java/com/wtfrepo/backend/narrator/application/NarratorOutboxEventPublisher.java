package com.wtfrepo.backend.narrator.application;

import com.wtfrepo.backend.narrator.application.support.NarratorConstants;
import com.wtfrepo.backend.narrator.domain.NarratorMode;
import com.wtfrepo.backend.narrator.domain.NarratorTone;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Centralized outbox publisher for narrator/ticker cross-module integration events. */
@Component
public class NarratorOutboxEventPublisher {

  private final OutboxEventStore outboxEventStore;

  public NarratorOutboxEventPublisher(OutboxEventStore outboxEventStore) {
    this.outboxEventStore = outboxEventStore;
  }

  public void publishPreferenceChanged(
      String userId,
      NarratorMode oldMode,
      NarratorMode newMode,
      NarratorTone tonePreference,
      String idempotencyKey) {
    String normalizedUserId = requireText(userId, "userId");
    String normalizedIdempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    NarratorPreferenceChangedPayload payload =
        new NarratorPreferenceChangedPayload(
            normalizedUserId,
            oldMode == null ? null : oldMode.name(),
            newMode == null ? null : newMode.name(),
            tonePreference == null ? null : tonePreference.name());
    outboxEventStore.append(
        new OutboxEventCommand(
            NarratorConstants.Outbox.AGGREGATE_TYPE,
            normalizedUserId,
            NarratorConstants.Outbox.EVENT_PREFERENCE_CHANGED,
            eventKey(normalizedUserId, normalizedIdempotencyKey),
            payload,
            Instant.now()));
  }

  public void publishTickerItemClicked(
      String userId, String eventId, String eventType, String actionUrl, String idempotencyKey) {
    String normalizedUserId = requireText(userId, "userId");
    String normalizedEventId = requireText(eventId, "eventId");
    String normalizedEventType = requireText(eventType, "eventType");
    String normalizedIdempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    TickerItemClickedPayload payload =
        new TickerItemClickedPayload(
            normalizedUserId, normalizedEventId, normalizedEventType, trimToNull(actionUrl));
    outboxEventStore.append(
        new OutboxEventCommand(
            NarratorConstants.Outbox.AGGREGATE_TYPE_TICKER,
            normalizedUserId,
            NarratorConstants.Outbox.EVENT_TICKER_ITEM_CLICKED,
            eventKey(
                "narrator:ticker-click",
                normalizedUserId,
                normalizedEventId,
                normalizedIdempotencyKey),
            payload,
            Instant.now()));
  }

  private String eventKey(String userId, String idempotencyKey) {
    String stable = "narrator:pref:" + userId + ":" + idempotencyKey;
    return "narrator:pref:" + userId + ":" + shortSha256(stable);
  }

  private String eventKey(String prefix, String userId, String eventId, String idempotencyKey) {
    String stable = prefix + ":" + userId + ":" + eventId + ":" + idempotencyKey;
    return prefix + ":" + userId + ":" + shortSha256(stable);
  }

  private String requireText(String value, String fieldName) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value.trim();
  }

  private String trimToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String shortSha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hashed.length * 2);
      for (byte item : hashed) {
        builder.append(String.format("%02x", item));
      }
      return builder.substring(0, 24);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("sha256_not_available", ex);
    }
  }

  public record NarratorPreferenceChangedPayload(
      String userId, String oldMode, String newMode, String tonePreference) {}

  public record TickerItemClickedPayload(
      String userId, String eventId, String eventType, String actionUrl) {}
}
