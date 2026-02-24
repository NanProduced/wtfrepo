package com.wtfrepo.backend.shared.security;

import com.wtfrepo.backend.auth.domain.UserRole;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.token")
public class SecurityTokenProperties {

  private String secret = "change-this-in-prod-change-this-in-prod";
  private String issuer = "wtf-repo-backend";
  private Duration ttl = Duration.ofHours(1);
  private Duration adminTtl = Duration.ofHours(8);
  private String blacklistKeyPrefix = "security:token:blacklist";

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

  public Duration getAdminTtl() {
    return adminTtl;
  }

  public void setAdminTtl(Duration adminTtl) {
    this.adminTtl = adminTtl;
  }

  public String getBlacklistKeyPrefix() {
    return blacklistKeyPrefix;
  }

  public void setBlacklistKeyPrefix(String blacklistKeyPrefix) {
    this.blacklistKeyPrefix = blacklistKeyPrefix;
  }

  public Duration resolveTtl(Set<UserRole> roles) {
    if (roles == null || roles.isEmpty()) {
      return ttl;
    }
    if (roles.contains(UserRole.ADMIN) || roles.contains(UserRole.MANAGER)) {
      return adminTtl != null ? adminTtl : ttl;
    }
    return ttl;
  }
}
