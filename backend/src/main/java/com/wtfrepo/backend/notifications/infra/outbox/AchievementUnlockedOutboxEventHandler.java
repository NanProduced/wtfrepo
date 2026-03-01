package com.wtfrepo.backend.notifications.infra.outbox;

import com.wtfrepo.backend.notifications.application.NotificationOutboxEventConsumerService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Handles M08 {@code AchievementUnlockedEvent} for notifications inbox. */
@Component("notificationAchievementUnlockedOutboxEventHandler")
@ConditionalOnBean(NotificationOutboxEventConsumerService.class)
public class AchievementUnlockedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(AchievementUnlockedOutboxEventHandler.class);

  private static final String EVENT_TYPE = "AchievementUnlockedEvent";

  private final NotificationOutboxPayloadReader payloadReader;
  private final NotificationOutboxEventConsumerService consumerService;

  public AchievementUnlockedOutboxEventHandler(
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
    String userId = payloadReader.readTextField(message.payload(), "userId").orElse(null);
    String achievementCode =
        payloadReader.readTextField(message.payload(), "achievementCode").orElse(null);
    String displayName = payloadReader.readTextField(message.payload(), "displayName").orElse(null);
    Integer rewardBug = payloadReader.readIntField(message.payload(), "rewardBug").orElse(0);

    if (userId == null || achievementCode == null) {
      log.warn(
          "notify_outbox_achievement_unlocked_invalid eventId={} reason=missing_fields",
          message.eventId());
      return;
    }

    consumerService.onAchievementUnlocked(
        message.eventId(), userId, achievementCode, displayName, rewardBug, message.occurredAt());
  }
}
