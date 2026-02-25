package com.wtfrepo.backend.notifications.infra.outbox;

import com.wtfrepo.backend.economy.application.support.EconomyConstants;
import com.wtfrepo.backend.notifications.application.WalletOutboxEventConsumerService;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Handles M03 {@code BugBalanceChangedEvent} for wallet SSE publish. */
@Component("notificationBugBalanceChangedOutboxEventHandler")
@ConditionalOnBean(WalletOutboxEventConsumerService.class)
public class BugBalanceChangedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(BugBalanceChangedOutboxEventHandler.class);

  private static final String EVENT_TYPE = EconomyConstants.Outbox.EVENT_BUG_BALANCE_CHANGED;

  private final NotificationOutboxPayloadReader payloadReader;
  private final WalletOutboxEventConsumerService walletConsumerService;

  public BugBalanceChangedOutboxEventHandler(
      NotificationOutboxPayloadReader payloadReader,
      WalletOutboxEventConsumerService walletConsumerService) {
    this.payloadReader = payloadReader;
    this.walletConsumerService = walletConsumerService;
  }

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  public void handle(OutboxStreamMessage message) {
    String userId = payloadReader.readTextField(message.payload(), "userId").orElse(null);
    Long balanceAfter =
        payloadReader.readLongField(message.payload(), "balanceAfter").orElse(null);
    Long delta = payloadReader.readLongField(message.payload(), "delta").orElse(null);
    String reason = payloadReader.readTextField(message.payload(), "reason").orElse(null);
    String refId = payloadReader.readTextField(message.payload(), "refId").orElse(null);

    if (userId == null || balanceAfter == null || delta == null) {
      log.warn(
          "wallet_balance_changed_event_invalid eventId={} reason=missing_fields",
          message.eventId());
      return;
    }

    walletConsumerService.onBugBalanceChanged(
        message.eventId(), userId, balanceAfter, delta, reason, refId);
  }
}
