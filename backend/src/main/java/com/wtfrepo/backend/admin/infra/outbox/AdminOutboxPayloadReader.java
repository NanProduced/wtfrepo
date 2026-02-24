package com.wtfrepo.backend.admin.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Lightweight payload reader for Admin outbox handlers.
 *
 * <p>All helpers are tolerant to malformed payload and return empty optional instead of throwing.
 */
@Component
public class AdminOutboxPayloadReader {

  private final ObjectMapper objectMapper;

  public AdminOutboxPayloadReader(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Optional<String> readTextField(String payload, String fieldName) {
    if (!StringUtils.hasText(fieldName)) {
      return Optional.empty();
    }
    return parsePayload(payload)
        .map(node -> node.path(fieldName))
        .filter(JsonNode::isTextual)
        .map(JsonNode::asText)
        .map(String::trim)
        .filter(StringUtils::hasText);
  }

  public Optional<String> readEnumLikeUppercaseField(String payload, String fieldName) {
    return readTextField(payload, fieldName).map(value -> value.toUpperCase(Locale.ROOT));
  }

  private Optional<JsonNode> parsePayload(String payload) {
    if (!StringUtils.hasText(payload)) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readTree(payload));
    } catch (JsonProcessingException ignored) {
      return Optional.empty();
    }
  }
}
