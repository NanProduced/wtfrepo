package com.wtfrepo.backend.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled driver for Redis Stream outbox consumer polling loop. */
@Component
@ConditionalOnBean({RedisConnectionFactory.class, RedisStreamOutboxConsumerService.class})
@ConditionalOnProperty(prefix = "app.shared.outbox", name = "consumer-enabled", havingValue = "true")
public class OutboxStreamConsumerScheduler {

  private static final Logger log = LoggerFactory.getLogger(OutboxStreamConsumerScheduler.class);

  private final RedisStreamOutboxConsumerService consumerService;

  public OutboxStreamConsumerScheduler(RedisStreamOutboxConsumerService consumerService) {
    this.consumerService = consumerService;
  }

  @Scheduled(fixedDelayString = "${app.shared.outbox.consumer-fixed-delay-ms:500}")
  public void poll() {
    RedisStreamOutboxConsumerService.ConsumerBatchResult result = consumerService.pollAndDispatch();
    if (result.scannedCount() == 0) {
      return;
    }

    log.info(
        "outbox_stream_consumer_tick scanned={} consumed={} failed={} unknown={} acked={}",
        result.scannedCount(),
        result.consumedCount(),
        result.failedCount(),
        result.unknownCount(),
        result.ackedCount());
  }
}
