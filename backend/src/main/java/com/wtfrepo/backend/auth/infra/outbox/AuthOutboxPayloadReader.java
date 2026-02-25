package com.wtfrepo.backend.auth.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Lightweight payload reader for auth outbox handlers. */
@Component
public class AuthOutboxPayloadReader {

  private final ObjectMapper objectMapper;

  public AuthOutboxPayloadReader(ObjectMapper objectMapper) {
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

  public Optional<Long> readLongField(String payload, String fieldName) {
    if (!StringUtils.hasText(fieldName)) {
      return Optional.empty();
    }
    return parsePayload(payload).map(node -> node.path(fieldName)).flatMap(this::toLong);
  }

  private Optional<Long> toLong(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return Optional.empty();
    }
    if (node.isLong() || node.isInt()) {
      return Optional.of(node.longValue());
    }
    if (node.isTextual()) {
      String raw = node.asText();
      if (!StringUtils.hasText(raw)) {
        return Optional.empty();
      }
      try {
        return Optional.of(Long.parseLong(raw.trim()));
      } catch (NumberFormatException ignored) {
        return Optional.empty();
      }
    }
    return Optional.empty();
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
