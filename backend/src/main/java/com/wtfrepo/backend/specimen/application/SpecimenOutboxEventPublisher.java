package com.wtfrepo.backend.specimen.application;

import com.wtfrepo.backend.specimen.domain.SpecimenStatus;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Centralized outbox event publisher for M04 specimen lifecycle and match-config events.
 *
 * <p>This component keeps event naming/payload/key conventions in one place so application
 * services stay focused on domain workflow.
 */
@Component
public class SpecimenOutboxEventPublisher {

  private static final String AGGREGATE_TYPE_SPECIMEN = "SPECIMEN";
  private static final String AGGREGATE_TYPE_SPECIMEN_CONFIG = "SPECIMEN_CONFIG";

  private static final String SPECIMEN_ACTIVATED_EVENT = "SpecimenActivatedEvent";
  private static final String SPECIMEN_DEACTIVATED_EVENT = "SpecimenDeactivatedEvent";
  private static final String SPECIMEN_TAGS_CHANGED_EVENT = "SpecimenTagsChangedEvent";
  private static final String MATCH_CONFIG_CHANGED_EVENT = "MatchConfigChangedEvent";

  private final OutboxEventStore outboxEventStore;

  public SpecimenOutboxEventPublisher(OutboxEventStore outboxEventStore) {
    this.outboxEventStore = outboxEventStore;
  }

  public void publishSpecimenActivated(
      String specimenId,
      String species,
      List<String> diagnosisTags,
      SpecimenStatus status,
      String idempotencyKey) {
    SpecimenActivatedEventPayload payload =
        new SpecimenActivatedEventPayload(
            specimenId,
            trimToNull(species),
            canonicalTags(diagnosisTags),
            status == null ? null : status.name());
    append(
        AGGREGATE_TYPE_SPECIMEN,
        specimenId,
        SPECIMEN_ACTIVATED_EVENT,
        eventKey("specimen:activated", specimenId, idempotencyKey),
        payload);
  }

  public void publishSpecimenDeactivated(String specimenId, String idempotencyKey) {
    SpecimenDeactivatedEventPayload payload = new SpecimenDeactivatedEventPayload(specimenId);
    append(
        AGGREGATE_TYPE_SPECIMEN,
        specimenId,
        SPECIMEN_DEACTIVATED_EVENT,
        eventKey("specimen:deactivated", specimenId, idempotencyKey),
        payload);
  }

  public void publishSpecimenTagsChanged(
      String specimenId,
      String oldSpecies,
      String newSpecies,
      List<String> oldDiagnosisTags,
      List<String> newDiagnosisTags,
      String idempotencyKey) {
    SpecimenTagsChangedEventPayload payload =
        new SpecimenTagsChangedEventPayload(
            specimenId,
            trimToNull(oldSpecies),
            trimToNull(newSpecies),
            canonicalTags(oldDiagnosisTags),
            canonicalTags(newDiagnosisTags));
    append(
        AGGREGATE_TYPE_SPECIMEN,
        specimenId,
        SPECIMEN_TAGS_CHANGED_EVENT,
        eventKey("specimen:tags-changed", specimenId, idempotencyKey),
        payload);
  }

  public void publishMatchConfigChanged(String configType, String version, String idempotencyKey) {
    String normalizedConfigType = normalizeConfigType(configType);
    MatchConfigChangedEventPayload payload =
        new MatchConfigChangedEventPayload(normalizedConfigType, trimToNull(version));
    append(
        AGGREGATE_TYPE_SPECIMEN_CONFIG,
        normalizedConfigType,
        MATCH_CONFIG_CHANGED_EVENT,
        eventKey("specimen:match-config-changed", normalizedConfigType, version, idempotencyKey),
        payload);
  }

  private void append(
      String aggregateType, String aggregateId, String eventType, String eventKey, Object payload) {
    outboxEventStore.append(
        new OutboxEventCommand(
            aggregateType,
            aggregateId,
            eventType,
            eventKey,
            payload,
            Instant.now()));
  }

  private String eventKey(String prefix, String aggregateId, String idempotencyKey) {
    return eventKey(prefix, aggregateId, null, idempotencyKey);
  }

  private String eventKey(
      String prefix, String aggregateId, String extraDiscriminator, String idempotencyKey) {
    String normalizedAggregateId = normalizeText(aggregateId);
    String normalizedExtra = normalizeText(extraDiscriminator);
    String stablePart =
        String.join(
            "|",
            normalizeText(prefix),
            normalizedAggregateId,
            normalizedExtra,
            normalizeText(idempotencyKey));
    String digest = shortSha256(stablePart);

    if (!StringUtils.hasText(normalizedExtra)) {
      return prefix + ":" + normalizedAggregateId + ":" + digest;
    }
    return prefix + ":" + normalizedAggregateId + ":" + normalizedExtra + ":" + digest;
  }

  private String normalizeConfigType(String configType) {
    return normalizeText(configType).toUpperCase(Locale.ROOT);
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
      // 24 chars gives enough uniqueness while keeping event_key compact.
      return builder.substring(0, 24);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("sha256_not_available", ex);
    }
  }

  private String normalizeText(String value) {
    return StringUtils.hasText(value) ? value.trim() : "-";
  }

  private List<String> canonicalTags(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return List.of();
    }
    return tags.stream()
        .map(this::trimToNull)
        .filter(StringUtils::hasText)
        .distinct()
        .sorted(Comparator.naturalOrder())
        .toList();
  }

  public record SpecimenActivatedEventPayload(
      String specimenId, String species, List<String> diagnosisTags, String status) {}

  public record SpecimenDeactivatedEventPayload(String specimenId) {}

  public record SpecimenTagsChangedEventPayload(
      String specimenId,
      String oldSpecies,
      String newSpecies,
      List<String> oldDiagnosisTags,
      List<String> newDiagnosisTags) {}

  public record MatchConfigChangedEventPayload(String configType, String version) {}
}
