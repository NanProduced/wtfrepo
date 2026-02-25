package com.wtfrepo.backend.notifications.infra.outbox;

import com.wtfrepo.backend.notifications.application.NotificationOutboxEventConsumerService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles M05 {@code CommentMentionEvent} for notifications inbox. */
@Component
public class CommentMentionOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(CommentMentionOutboxEventHandler.class);

  private static final String EVENT_TYPE = "CommentMentionEvent";

  private final NotificationOutboxPayloadReader payloadReader;
  private final NotificationOutboxEventConsumerService consumerService;

  public CommentMentionOutboxEventHandler(
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
    List<String> mentionedUserIds =
        payloadReader.readStringListField(message.payload(), "mentionedUserIds");

    if (commentId == null || specimenId == null || authorUserId == null) {
      log.warn(
          "notify_outbox_comment_mention_invalid eventId={} reason=missing_fields",
          message.eventId());
      return;
    }

    consumerService.onCommentMention(
        message.eventId(),
        commentId,
        specimenId,
        authorUserId,
        mentionedUserIds,
        message.occurredAt());
  }
}
