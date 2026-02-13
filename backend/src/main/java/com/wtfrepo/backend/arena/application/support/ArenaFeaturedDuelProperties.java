package com.wtfrepo.backend.arena.application.support;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable featured-duel cache properties.
 *
 * <p>TODO(M03-economy): move these runtime values to admin-published policy source.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.arena.featured-duel")
public class ArenaFeaturedDuelProperties {

  private String cacheKey = "arena:duel:featured:v1";

  private Duration cacheTtl = Duration.ofSeconds(30);
}

