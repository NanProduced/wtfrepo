package com.wtfrepo.backend.auth.application;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.auth.rate-limit")
public class AuthRateLimitProperties {

  private int exchangeLimit = 30;
  private Duration exchangeWindow = Duration.ofMinutes(1);
  private int renameLimit = 10;
  private Duration renameWindow = Duration.ofMinutes(1);

}
