package com.wtfrepo.backend.auth.application;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.auth.contract")
public class AuthContractProperties {

  private Duration oauthStateTtl = Duration.ofMinutes(10);
  private Duration exchangeIdempotencyTtl = Duration.ofMinutes(10);
  private List<String> forbiddenUsernameKeywords = List.of("admin", "manager", "root");

}
