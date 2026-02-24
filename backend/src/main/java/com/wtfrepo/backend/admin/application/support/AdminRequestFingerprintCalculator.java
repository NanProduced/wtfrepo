package com.wtfrepo.backend.admin.application.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/** Builds deterministic request fingerprints for admin idempotency. */
@Component
public class AdminRequestFingerprintCalculator {

  public String fingerprint(String payload) {
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
