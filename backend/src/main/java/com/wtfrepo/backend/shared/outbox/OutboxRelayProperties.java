package com.wtfrepo.backend.shared.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

/** Runtime controls for outbox persistence and relay behavior. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.shared.outbox")
public class OutboxRelayProperties {

  /**
   * Enables scheduled relay from outbox table to MQ transport.
   *
   * <p>Default is enabled for cross-module integration environments.
   */
  private boolean relayEnabled = true;

  /** Number of pending outbox rows processed per relay tick. */
  private int relayBatchSize = 100;

  /** Scheduler fixed delay in milliseconds for relay polling loop. */
  private long relayFixedDelayMs = 1000L;

  /** Max publish attempts before an event is marked FAILED. */
  private int relayMaxAttempts = 20;

  /** Target Redis Stream key where outbox events are published. */
  private String redisStreamKey = "wtfrepo:outbox:events";

  /**
   * Enables Redis Stream consumer-group polling loop.
   *
   * <p>Default is enabled for cross-module integration environments.
   */
  private boolean consumerEnabled = true;

  /** Consumer group name used by shared outbox stream poller. */
  private String consumerGroup = "wtfrepo:outbox:consumer";

  /** Consumer name within the group; can be overridden per instance. */
  private String consumerName = "backend-consumer";

  /** Number of stream entries read per consumer poll tick. */
  private int consumerBatchSize = 100;

  /** Blocking read timeout (ms) for XREADGROUP. */
  private long consumerBlockMs = 1000L;

  /** Scheduler fixed delay (ms) between consumer polling ticks. */
  private long consumerFixedDelayMs = 500L;

  /**
   * Whether unknown event types should be acknowledged.
   *
   * <p>Default false keeps unknown events pending for later compatible consumers.
   */
  private boolean consumerAckUnknownEventType = false;

  /** Enables JPA-backed deduplication for consumer side idempotency. */
  private boolean consumerDedupeEnabled = true;

  /** Retention window for processed consumer dedupe rows. */
  private Duration consumerDedupeRetention = Duration.ofDays(30);
}
