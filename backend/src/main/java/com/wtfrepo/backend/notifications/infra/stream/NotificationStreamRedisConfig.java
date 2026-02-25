package com.wtfrepo.backend.notifications.infra.stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis pub/sub wiring for notification SSE stream. */
@Configuration
@ConditionalOnBean(StringRedisTemplate.class)
public class NotificationStreamRedisConfig {

  @Bean
  public RedisMessageListenerContainer notificationStreamRedisContainer(
      RedisConnectionFactory connectionFactory, NotificationStreamMessageListener listener) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(listener, new PatternTopic("pager:*"));
    container.addMessageListener(listener, new PatternTopic("wallet:*"));
    container.addMessageListener(listener, new PatternTopic("narrator*"));
    container.addMessageListener(listener, new PatternTopic("ticker*"));
    return container;
  }
}
