package com.wtfrepo.backend.auth.infra.outbox;

import com.wtfrepo.backend.auth.application.AuthWalletSnapshotService;
import com.wtfrepo.backend.economy.application.support.EconomyConstants;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Syncs auth-side wallet snapshot on economy balance changes. */
@Component("authBugBalanceChangedOutboxEventHandler")
@ConditionalOnBean(AuthWalletSnapshotService.class)
public class BugBalanceChangedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(BugBalanceChangedOutboxEventHandler.class);

  private static final String EVENT_TYPE = EconomyConstants.Outbox.EVENT_BUG_BALANCE_CHANGED;

  private final AuthOutboxPayloadReader payloadReader;
  private final AuthWalletSnapshotService walletSnapshotService;

  public BugBalanceChangedOutboxEventHandler(
      AuthOutboxPayloadReader payloadReader,
      AuthWalletSnapshotService walletSnapshotService) {
    this.payloadReader = payloadReader;
    this.walletSnapshotService = walletSnapshotService;
  }

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  public void handle(OutboxStreamMessage message) {
    String userId = payloadReader.readTextField(message.payload(), "userId").orElse(null);
    Long balanceAfter = payloadReader.readLongField(message.payload(), "balanceAfter").orElse(null);
    if (userId == null || balanceAfter == null) {
      log.warn(
          "auth_wallet_snapshot_skip eventId={} reason=missing_fields",
          message.eventId());
      return;
    }
    walletSnapshotService.applyBugBalance(userId, balanceAfter);
  }
}
