package com.wtfrepo.backend.notifications.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wtfrepo.backend.shared.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Publishes wallet SSE payloads from economy outbox events. */
@Service
public class WalletOutboxEventConsumerService {

  private static final Logger log =
      LoggerFactory.getLogger(WalletOutboxEventConsumerService.class);

  private static final String CHANNEL_PREFIX = "wallet:";
  private static final String CHANNEL_NAME = "wallet";
  private static final String TYPE_BALANCE_CHANGED = "BALANCE_CHANGED";

  private final StringRedisTemplate redisTemplate;
  private final JsonUtils jsonUtils;

  public WalletOutboxEventConsumerService(StringRedisTemplate redisTemplate, JsonUtils jsonUtils) {
    this.redisTemplate = redisTemplate;
    this.jsonUtils = jsonUtils;
  }

  public void onBugBalanceChanged(
      String eventId,
      String userId,
      long balanceAfter,
      long delta,
      String reason,
      String refId) {
    if (!StringUtils.hasText(userId)) {
      log.warn("wallet_balance_changed_skip eventId={} reason=missing_user_id", eventId);
      return;
    }

    WalletBalanceChangedPayload payload =
        new WalletBalanceChangedPayload(
            CHANNEL_NAME, TYPE_BALANCE_CHANGED, balanceAfter, delta, reason, refId);
    String json;
    try {
      json = jsonUtils.toJson(payload);
    } catch (JsonProcessingException ex) {
      log.warn("wallet_balance_changed_skip eventId={} reason=serialize_failed", eventId, ex);
      return;
    }

    try {
      redisTemplate.convertAndSend(CHANNEL_PREFIX + userId.trim(), json);
    } catch (RuntimeException ex) {
      log.warn("wallet_balance_changed_publish_failed eventId={}", eventId, ex);
    }
  }

  private record WalletBalanceChangedPayload(
      String channel, String type, long balanceAfter, long delta, String reason, String refId) {}
}
