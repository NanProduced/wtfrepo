package com.wtfrepo.backend.auth.infra.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.wtfrepo.backend.auth.application.AuthIdentityProofProperties;
import com.wtfrepo.backend.auth.application.OAuthIdentityVerifier;
import com.wtfrepo.backend.auth.application.support.AuthConstants;
import com.wtfrepo.backend.auth.application.support.AuthExceptions;
import com.wtfrepo.backend.auth.domain.OAuthProvider;
import com.wtfrepo.backend.shared.web.ApiException;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.springframework.stereotype.Component;

/**
 * Verifies internal identityProof JWT produced by BFF.
 *
 * <p>This verifier intentionally trusts only backend-local configuration and signature,
 * instead of calling OAuth providers directly.
 */
@Component
public class JwtOAuthIdentityVerifier implements OAuthIdentityVerifier {

  private final AuthIdentityProofProperties properties;
  private final Clock clock;

  public JwtOAuthIdentityVerifier(AuthIdentityProofProperties properties) {
    this(properties, Clock.systemUTC());
  }

  JwtOAuthIdentityVerifier(AuthIdentityProofProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public String verifyAndResolveSubject(OAuthProvider provider, String identityProof) {
    SignedJWT signedJwt = parse(identityProof);
    verifySignature(signedJwt);

    JWTClaimsSet claims = claims(signedJwt);
    validateCoreClaims(claims);
    validateProviderClaim(provider, claims);
    validateTimeClaims(claims);

    return claims.getSubject();
  }

  private SignedJWT parse(String identityProof) {
    if (identityProof == null || identityProof.isBlank()) {
      throw unauthorized(AuthConstants.Message.INVALID_IDENTITY_PROOF);
    }
    try {
      return SignedJWT.parse(identityProof);
    } catch (ParseException ex) {
      throw unauthorized(AuthConstants.Message.INVALID_IDENTITY_PROOF);
    }
  }

  private void verifySignature(SignedJWT signedJwt) {
    // v0.1 only accepts HS256 to keep BFF/backend integration explicit and simple.
    if (!JWSAlgorithm.HS256.equals(signedJwt.getHeader().getAlgorithm())) {
      throw unauthorized(AuthConstants.Message.UNSUPPORTED_IDENTITY_PROOF_ALGORITHM);
    }
    try {
      boolean valid = signedJwt.verify(new MACVerifier(properties.secretKey().getEncoded()));
      if (!valid) {
        throw unauthorized(AuthConstants.Message.INVALID_IDENTITY_PROOF_SIGNATURE);
      }
    } catch (JOSEException ex) {
      throw unauthorized(AuthConstants.Message.INVALID_IDENTITY_PROOF_SIGNATURE);
    }
  }

  private JWTClaimsSet claims(SignedJWT signedJwt) {
    try {
      return signedJwt.getJWTClaimsSet();
    } catch (ParseException ex) {
      throw unauthorized(AuthConstants.Message.INVALID_IDENTITY_PROOF_CLAIMS);
    }
  }

  private void validateCoreClaims(JWTClaimsSet claims) {
    if (!properties.getIssuer().equals(claims.getIssuer())) {
      throw unauthorized(AuthConstants.Message.INVALID_IDENTITY_PROOF_ISSUER);
    }
    if (claims.getAudience() == null || !claims.getAudience().contains(properties.getAudience())) {
      throw unauthorized(AuthConstants.Message.INVALID_IDENTITY_PROOF_AUDIENCE);
    }

    requireNonBlank(claims.getSubject(), AuthConstants.Message.INVALID_IDENTITY_PROOF_SUBJECT);
    requireNonBlank(claims.getJWTID(), AuthConstants.Message.INVALID_IDENTITY_PROOF_JTI);
  }

  private void validateProviderClaim(OAuthProvider provider, JWTClaimsSet claims) {
    // Provider in request body must match provider claim in signed proof.
    String tokenProvider = stringClaim(claims, AuthConstants.IdentityProof.PROVIDER_CLAIM);
    if (!provider.name().equals(tokenProvider)) {
      throw unauthorized(AuthConstants.Message.IDENTITY_PROOF_PROVIDER_MISMATCH);
    }
  }

  private String stringClaim(JWTClaimsSet claims, String key) {
    try {
      String value = claims.getStringClaim(key);
      if (value == null || value.isBlank()) {
        throw unauthorized(AuthConstants.Message.INVALID_IDENTITY_PROOF_CLAIM_PREFIX + key);
      }
      return value;
    } catch (ParseException ex) {
      throw unauthorized(AuthConstants.Message.INVALID_IDENTITY_PROOF_CLAIM_PREFIX + key);
    }
  }

  private void validateTimeClaims(JWTClaimsSet claims) {
    Date issuedAtDate = claims.getIssueTime();
    Date expiresAtDate = claims.getExpirationTime();
    if (issuedAtDate == null || expiresAtDate == null) {
      throw unauthorized(AuthConstants.Message.MISSING_IDENTITY_PROOF_TEMPORAL_CLAIMS);
    }

    Instant issuedAt = issuedAtDate.toInstant();
    Instant expiresAt = expiresAtDate.toInstant();

    // Enforce bounded token lifetime to reduce replay window risk.
    Duration ttl = Duration.between(issuedAt, expiresAt);
    if (ttl.isNegative() || ttl.isZero()) {
      throw unauthorized(AuthConstants.Message.INVALID_IDENTITY_PROOF_LIFETIME);
    }
    if (ttl.compareTo(properties.getMaxTtl()) > 0) {
      throw unauthorized(AuthConstants.Message.IDENTITY_PROOF_LIFETIME_EXCEEDED);
    }

    Duration clockSkew = properties.getClockSkew();
    Instant now = clock.instant();
    if (now.isBefore(issuedAt.minus(clockSkew))) {
      throw unauthorized(AuthConstants.Message.IDENTITY_PROOF_NOT_ACTIVE);
    }
    if (!now.isBefore(expiresAt.plus(clockSkew))) {
      throw unauthorized(AuthConstants.Message.IDENTITY_PROOF_EXPIRED);
    }

    Date notBeforeDate = claims.getNotBeforeTime();
    if (notBeforeDate != null && now.isBefore(notBeforeDate.toInstant().minus(clockSkew))) {
      throw unauthorized(AuthConstants.Message.IDENTITY_PROOF_NOT_ACTIVE);
    }
  }

  private void requireNonBlank(String value, String message) {
    if (value == null || value.isBlank()) {
      throw unauthorized(message);
    }
  }

  private ApiException unauthorized(String message) {
    return AuthExceptions.unauthorized(message);
  }
}
