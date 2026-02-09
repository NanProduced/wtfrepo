package com.wtfrepo.backend.shared.security;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.token")
public class SecurityTokenProperties {

  private String secret = "change-this-in-prod-change-this-in-prod";
  private String issuer = "wtf-repo-backend";
  private Duration ttl = Duration.ofHours(1);

  @PostConstruct
  void validate() {
    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException("app.security.token.secret must be at least 32 chars");
    }
  }

  public SecretKey secretKey() {
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public Duration getTtl() {
    return ttl;
  }

  public void setTtl(Duration ttl) {
    this.ttl = ttl;
  }
}

