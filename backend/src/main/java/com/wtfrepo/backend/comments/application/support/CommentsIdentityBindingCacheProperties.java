package com.wtfrepo.backend.comments.application.support;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Redis cache properties for comment author identity bindings. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.comments.identity-binding-cache")
public class CommentsIdentityBindingCacheProperties {

  private String cacheKeyPrefix = "comments:identity-binding";

  private Duration ttl = Duration.ofMinutes(5);
}
