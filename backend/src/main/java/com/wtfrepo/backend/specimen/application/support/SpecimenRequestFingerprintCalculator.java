package com.wtfrepo.backend.specimen.application.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wtfrepo.backend.shared.json.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * Produces deterministic SHA-256 request fingerprints for idempotent write operations.
 */
@Component
public class SpecimenRequestFingerprintCalculator {

  private final JsonUtils jsonUtils;

  public SpecimenRequestFingerprintCalculator(JsonUtils jsonUtils) {
    this.jsonUtils = jsonUtils;
  }

  /**
   * Converts payload into canonical JSON and returns SHA-256 hex digest.
   */
  public String fingerprint(Object payload) {
    try {
      String json = jsonUtils.toJson(payload);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
      return toHex(hash);
    } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
      throw SpecimenExceptions.validation("invalid_payload");
    }
  }

  private String toHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      builder.append(String.format("%02x", value));
    }
    return builder.toString();
  }
}
