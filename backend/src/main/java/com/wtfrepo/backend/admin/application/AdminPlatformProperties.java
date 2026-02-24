package com.wtfrepo.backend.admin.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Property-backed configuration for admin platform workflows. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminPlatformProperties {

  private Bootstrap bootstrap = new Bootstrap();

  @Getter
  @Setter
  public static class Bootstrap {
    /** Expected email address for initial bootstrap validation. Empty means no check. */
    private String email = "";
  }
}
