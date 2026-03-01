package com.wtfrepo.backend.achievements.infra.outbox;

import com.wtfrepo.backend.achievements.application.AchievementInboxService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.springframework.stereotype.Component;

/** Captures ticker click interactions into achievements inbox. */
@Component
public class TickerItemClickedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final String EVENT_TYPE = "TickerItemClickedEvent";

  private final AchievementInboxService inboxService;

  public TickerItemClickedOutboxEventHandler(AchievementInboxService inboxService) {
    this.inboxService = inboxService;
  }

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  public void handle(OutboxStreamMessage message) {
    inboxService.recordTickerItemClicked(message);
  }
}
