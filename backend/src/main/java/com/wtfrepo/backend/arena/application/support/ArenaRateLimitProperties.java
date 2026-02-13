package com.wtfrepo.backend.arena.application.support;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configurable rate-limit settings for arena APIs. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.arena.rate-limit")
public class ArenaRateLimitProperties {

  private int duelAnonymousLimit = 60;

  private Duration duelAnonymousWindow = Duration.ofMinutes(1);

  private int duelAuthenticatedUserLimit = 120;

  private Duration duelAuthenticatedUserWindow = Duration.ofMinutes(1);

  private int duelAuthenticatedIpLimit = 120;

  private Duration duelAuthenticatedIpWindow = Duration.ofMinutes(1);
}

