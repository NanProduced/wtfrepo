package com.wtfrepo.backend.notifications.infra.outbox;

import com.wtfrepo.backend.notifications.application.NotificationOutboxEventConsumerService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles M05 {@code CommentPublishedEvent} for notifications inbox. */
@Component
public class CommentPublishedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(CommentPublishedOutboxEventHandler.class);

  private static final String EVENT_TYPE = "CommentPublishedEvent";

  private final NotificationOutboxPayloadReader payloadReader;
  private final NotificationOutboxEventConsumerService consumerService;

  public CommentPublishedOutboxEventHandler(
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
    String authorUserId = payloadReader.readTextField(message.payload(), "authorUserId").orElse(null);
    String replyToUserId = payloadReader.readTextField(message.payload(), "replyToUserId").orElse(null);
    String status = payloadReader.readTextField(message.payload(), "status").orElse(null);

    if (commentId == null || specimenId == null || authorUserId == null || status == null) {
      log.warn(
          "notify_outbox_comment_published_invalid eventId={} reason=missing_fields",
          message.eventId());
      return;
    }

    consumerService.onCommentPublished(
        message.eventId(),
        commentId,
        specimenId,
        authorUserId,
        replyToUserId,
        status,
        message.occurredAt());
  }
}
