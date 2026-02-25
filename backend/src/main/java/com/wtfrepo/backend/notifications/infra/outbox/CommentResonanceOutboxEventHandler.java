package com.wtfrepo.backend.notifications.infra.outbox;

import com.wtfrepo.backend.notifications.application.NotificationOutboxEventConsumerService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Handles M05 {@code CommentResonanceEvent} for notifications inbox. */
@Component
@ConditionalOnBean(NotificationOutboxEventConsumerService.class)
public class CommentResonanceOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(CommentResonanceOutboxEventHandler.class);

  private static final String EVENT_TYPE = "CommentResonanceEvent";

  private final NotificationOutboxPayloadReader payloadReader;
  private final NotificationOutboxEventConsumerService consumerService;

  public CommentResonanceOutboxEventHandler(
      NotificationOutboxPayloadReader payloadReader,
      NotificationOutboxEventConsumerService consumerService) {
    this.payloadReader = payloadReader;
    this.consumerService = consumerService;
  }

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  public void handle(OutboxStreamMessage message) {
    String commentId = payloadReader.readTextField(message.payload(), "commentId").orElse(null);
    String specimenId = payloadReader.readTextField(message.payload(), "specimenId").orElse(null);
    String resonatorUserId =
        payloadReader.readTextField(message.payload(), "resonatorUserId").orElse(null);
    String commentAuthorUserId =
        payloadReader.readTextField(message.payload(), "commentAuthorUserId").orElse(null);

    if (commentId == null || specimenId == null || resonatorUserId == null || commentAuthorUserId == null) {
      log.warn(
          "notify_outbox_comment_resonance_invalid eventId={} reason=missing_fields",
          message.eventId());
      return;
    }

    consumerService.onCommentResonance(
        message.eventId(),
        commentId,
        specimenId,
        resonatorUserId,
        commentAuthorUserId,
        message.occurredAt());
  }
}
