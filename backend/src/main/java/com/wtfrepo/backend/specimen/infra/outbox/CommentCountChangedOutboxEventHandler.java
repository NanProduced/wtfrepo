package com.wtfrepo.backend.specimen.infra.outbox;

import com.wtfrepo.backend.comments.application.support.CommentsConstants;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import com.wtfrepo.backend.specimen.application.SpecimenCommunityMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles comment count changes for specimen community metrics. */
@Component
public class CommentCountChangedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(CommentCountChangedOutboxEventHandler.class);

  private static final String EVENT_TYPE = CommentsConstants.Outbox.EVENT_COMMENT_COUNT_CHANGED;

  private final SpecimenOutboxPayloadReader payloadReader;
  private final SpecimenCommunityMetricsService metricsService;

  public CommentCountChangedOutboxEventHandler(
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
    Integer delta = payloadReader.readIntField(message.payload(), "delta").orElse(null);

    if (specimenId == null || delta == null) {
      log.warn(
          "specimen_comment_count_event_invalid eventId={} reason=missing_fields",
          message.eventId());
      return;
    }

    metricsService.onCommentCountChanged(
        message.eventId(), specimenId, delta.longValue(), message.occurredAt());
  }
}
