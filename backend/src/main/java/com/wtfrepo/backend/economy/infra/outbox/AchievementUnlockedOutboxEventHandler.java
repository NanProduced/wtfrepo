package com.wtfrepo.backend.economy.infra.outbox;

import com.wtfrepo.backend.economy.application.BettingOutboxEventConsumerService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles M08 {@code AchievementUnlockedEvent} for achievement reward credit issuance. */
@Component("economyAchievementUnlockedOutboxEventHandler")
public class AchievementUnlockedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(AchievementUnlockedOutboxEventHandler.class);

  private static final String EVENT_TYPE = "AchievementUnlockedEvent";

  private final EconomyOutboxPayloadReader payloadReader;
  private final BettingOutboxEventConsumerService eventConsumerService;

  public AchievementUnlockedOutboxEventHandler(
      EconomyOutboxPayloadReader payloadReader,
      BettingOutboxEventConsumerService eventConsumerService) {
    this.payloadReader = payloadReader;
    this.eventConsumerService = eventConsumerService;
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
    Integer rewardBug = payloadReader.readIntField(message.payload(), "rewardBug").orElse(0);

    if (userId == null || achievementCode == null) {
      log.warn(
          "economy_outbox_payload_invalid eventType={} eventId={} reason=missing_required_fields",
          EVENT_TYPE,
          message.eventId());
      return;
    }

    eventConsumerService.onAchievementUnlocked(
        message.eventId(), userId, achievementCode, rewardBug, message.occurredAt());
  }
}
