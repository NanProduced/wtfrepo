package com.wtfrepo.backend.admin.application;

import lombok.Getter;
import lombok.Setter;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Property-backed configuration for admin platform workflows. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminPlatformProperties {

  private Bootstrap bootstrap = new Bootstrap();
  private OAuth oauth = new OAuth();

  @Getter
  @Setter
  public static class Bootstrap {
    /** Expected email address for initial bootstrap validation. Empty means no check. */
    private String email = "";
  }

  @Getter
  @Setter
  public static class OAuth {
    /** OAuth authorization code expiration window. */
    private Duration codeExpiration = Duration.ofMinutes(5);

    /** Whitelisted callback URIs for admin-lite OAuth flow. */
    private List<String> allowedRedirectUris = List.of("http://localhost:3001/auth/callback");
  }
}
