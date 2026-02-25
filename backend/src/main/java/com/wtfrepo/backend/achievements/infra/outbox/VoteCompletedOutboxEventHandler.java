package com.wtfrepo.backend.achievements.infra.outbox;

import com.wtfrepo.backend.achievements.application.AchievementInboxService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.springframework.stereotype.Component;

/** Captures arena vote completion events into achievements inbox. */
@Component
public class VoteCompletedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final String EVENT_TYPE = "VoteCompletedEvent";

  private final AchievementInboxService inboxService;

  public VoteCompletedOutboxEventHandler(AchievementInboxService inboxService) {
    this.inboxService = inboxService;
  }

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  public void handle(OutboxStreamMessage message) {
    inboxService.recordVoteCompleted(message);
  }
}
