package com.wtfrepo.backend.auth.application;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for internal `identityProof` JWT verification.
 *
 * <p>In v0.1, BFF issues a short-lived signed proof and backend verifies it during
 * `/api/v1/auth/exchange`.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.auth.identity-proof")
public class AuthIdentityProofProperties {

  /**
   * Expected `iss` of internal identityProof JWT issued by BFF.
   */
  @NotBlank
  private String issuer = "wtf-repo-bff";

  /**
   * Expected `aud` of internal identityProof JWT.
   */
  @NotBlank
  private String audience = "wtf-repo-backend-auth-exchange";

  /**
   * Shared secret used by BFF and backend for HS256 signing/verifying.
   */
  @NotBlank
  @Size(min = 32)
  private String secret = "change-this-identity-proof-secret-change-this";

  /**
   * Allowed clock drift between BFF and backend during temporal claims validation.
   */
  @NotNull
  private Duration clockSkew = Duration.ofSeconds(60);

  /**
   * Maximum accepted lifespan of identityProof (`exp - iat`).
   */
  @NotNull
  private Duration maxTtl = Duration.ofMinutes(5);

  @PostConstruct
  void validate() {
    if (clockSkew.isNegative()) {
      throw new IllegalStateException("app.auth.identity-proof.clock-skew must not be negative");
    }
    if (maxTtl.isNegative() || maxTtl.isZero()) {
      throw new IllegalStateException("app.auth.identity-proof.max-ttl must be greater than zero");
    }
  }

  /**
   * Returns HS256 key material for identity proof signature verification.
   */
  public SecretKey secretKey() {
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }
}

