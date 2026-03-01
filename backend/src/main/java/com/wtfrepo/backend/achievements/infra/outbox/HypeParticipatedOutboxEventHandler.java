package com.wtfrepo.backend.achievements.infra.outbox;

import com.wtfrepo.backend.achievements.application.AchievementInboxService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.springframework.stereotype.Component;

/** Captures specimen hype participation events into achievements inbox. */
@Component
public class HypeParticipatedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final String EVENT_TYPE = "HypeParticipatedEvent";

  private final AchievementInboxService inboxService;

  public HypeParticipatedOutboxEventHandler(AchievementInboxService inboxService) {
    this.inboxService = inboxService;
  }

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  public void handle(OutboxStreamMessage message) {
    inboxService.recordHypeParticipated(message);
  }
}
