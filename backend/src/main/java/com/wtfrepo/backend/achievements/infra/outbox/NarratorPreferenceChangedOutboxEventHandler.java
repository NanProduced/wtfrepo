package com.wtfrepo.backend.achievements.infra.outbox;

import com.wtfrepo.backend.achievements.application.AchievementInboxService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.springframework.stereotype.Component;

/** Captures narrator preference changes into achievements inbox. */
@Component
public class NarratorPreferenceChangedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final String EVENT_TYPE = "NarratorPreferenceChangedEvent";

  private final AchievementInboxService inboxService;

  public NarratorPreferenceChangedOutboxEventHandler(AchievementInboxService inboxService) {
    this.inboxService = inboxService;
  }

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  public void handle(OutboxStreamMessage message) {
    inboxService.recordNarratorPreferenceChanged(message);
  }
}
