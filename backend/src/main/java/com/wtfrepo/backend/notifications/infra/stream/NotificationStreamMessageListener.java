package com.wtfrepo.backend.notifications.infra.stream;

import com.wtfrepo.backend.notifications.application.NotificationStreamService;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Redis pub/sub listener that forwards payloads to SSE connections. */
@Component
public class NotificationStreamMessageListener implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(NotificationStreamMessageListener.class);

  private final NotificationStreamService streamService;

  public NotificationStreamMessageListener(NotificationStreamService streamService) {
    this.streamService = streamService;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    if (message == null) {
      return;
    }
    String channel = decode(message.getChannel());
    if (!StringUtils.hasText(channel)) {
      return;
    }
    String payload = decode(message.getBody());
    try {
      streamService.dispatch(channel, payload);
    } catch (RuntimeException ex) {
      log.warn("notify_stream_dispatch_failed channel={}", channel, ex);
    }
  }

  private String decode(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
