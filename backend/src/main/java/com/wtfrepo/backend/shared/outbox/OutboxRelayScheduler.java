package com.wtfrepo.backend.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled driver for outbox relay loop. */
@Component
@ConditionalOnBean(OutboxRelayService.class)
@ConditionalOnProperty(prefix = "app.shared.outbox", name = {"relay-enabled", "jpa-enabled"}, havingValue = "true")
public class OutboxRelayScheduler {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

  private final OutboxRelayService outboxRelayService;

  public OutboxRelayScheduler(OutboxRelayService outboxRelayService) {
    this.outboxRelayService = outboxRelayService;
  }

  @Scheduled(fixedDelayString = "${app.shared.outbox.relay-fixed-delay-ms:1000}")
  public void flushPendingEvents() {
    OutboxRelayService.RelayBatchResult result = outboxRelayService.relayPendingBatch();
    if (result.scannedCount() == 0) {
      return;
    }

    log.info(
        "outbox_relay_scheduled scanned={} published={} failed={}",
        result.scannedCount(),
        result.publishedCount(),
        result.failedCount());
  }
}
