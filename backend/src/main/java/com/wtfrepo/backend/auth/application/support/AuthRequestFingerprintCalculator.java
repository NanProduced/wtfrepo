package com.wtfrepo.backend.auth.application.support;

import com.wtfrepo.backend.auth.api.AuthExchangeRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * Builds deterministic request fingerprints for exchange idempotency.
 */
@Component
public class AuthRequestFingerprintCalculator {

  public String fingerprint(AuthExchangeRequest request) {
    String payload =
        request.provider().name() + "|" + request.identityProof() + "|" + request.oauthState();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
      return hex(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private String hex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte item : bytes) {
      sb.append(String.format("%02x", item));
    }
    return sb.toString();
  }
}
