package com.wtfrepo.backend.specimen.infra.outbox;

import com.wtfrepo.backend.comments.application.support.CommentsConstants;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import com.wtfrepo.backend.specimen.application.SpecimenCommunityMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles top-roast updates for specimen community metrics. */
@Component
public class TopRoastUpdatedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(TopRoastUpdatedOutboxEventHandler.class);

  private static final String EVENT_TYPE = CommentsConstants.Outbox.EVENT_TOP_ROAST_UPDATED;

  private final SpecimenOutboxPayloadReader payloadReader;
  private final SpecimenCommunityMetricsService metricsService;

  public TopRoastUpdatedOutboxEventHandler(
      SpecimenOutboxPayloadReader payloadReader,
      SpecimenCommunityMetricsService metricsService) {
    this.payloadReader = payloadReader;
    this.metricsService = metricsService;
  }

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  public void handle(OutboxStreamMessage message) {
    String specimenId =
        payloadReader.readTextField(message.payload(), "specimenId").orElse(null);
    String commentId =
        payloadReader.readTextField(message.payload(), "commentId").orElse(null);
    Integer resonanceCount =
        payloadReader.readIntField(message.payload(), "resonanceCount").orElse(0);
    Boolean chiefConclusion =
        payloadReader.readBooleanField(message.payload(), "chiefConclusion").orElse(false);

    if (specimenId == null || commentId == null) {
      log.warn(
          "specimen_top_roast_event_invalid eventId={} reason=missing_fields",
          message.eventId());
      return;
    }

    metricsService.onTopRoastUpdated(
        message.eventId(),
        specimenId,
        commentId,
        resonanceCount,
        chiefConclusion,
        message.occurredAt());
  }
}
