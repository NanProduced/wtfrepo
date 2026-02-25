package com.wtfrepo.backend.notifications.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Lightweight payload reader for notifications outbox handlers.
 *
 * <p>All methods are fail-safe by default and return empty optionals or empty lists on malformed
 * payload.
 */
@Component
public class NotificationOutboxPayloadReader {

  private final ObjectMapper objectMapper;

  public NotificationOutboxPayloadReader(ObjectMapper objectMapper) {
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

  public Optional<Integer> readIntField(String payload, String fieldName) {
    if (!StringUtils.hasText(fieldName)) {
      return Optional.empty();
    }
    return parsePayload(payload).map(node -> node.path(fieldName)).flatMap(this::toInt);
  }

  public Optional<Long> readLongField(String payload, String fieldName) {
    if (!StringUtils.hasText(fieldName)) {
      return Optional.empty();
    }
    return parsePayload(payload).map(node -> node.path(fieldName)).flatMap(this::toLong);
  }

  public List<String> readStringListField(String payload, String fieldName) {
    if (!StringUtils.hasText(fieldName)) {
      return List.of();
    }
    return parsePayload(payload)
        .map(node -> node.path(fieldName))
        .filter(JsonNode::isArray)
        .map(
            array -> {
              List<String> values = new ArrayList<>();
              for (JsonNode item : array) {
                if (item != null && item.isTextual()) {
                  String value = item.asText();
                  if (StringUtils.hasText(value)) {
                    values.add(value.trim());
                  }
                }
              }
              return values;
            })
        .orElse(Collections.emptyList());
  }

  private Optional<Integer> toInt(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return Optional.empty();
    }

    if (node.isInt() || node.isLong()) {
      return Optional.of(node.intValue());
    }

    if (node.isTextual()) {
      String rawValue = node.asText();
      if (!StringUtils.hasText(rawValue)) {
        return Optional.empty();
      }
      try {
        return Optional.of(Integer.parseInt(rawValue.trim()));
      } catch (NumberFormatException ignored) {
        return Optional.empty();
      }
    }

    return Optional.empty();
  }

  private Optional<Long> toLong(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return Optional.empty();
    }

    if (node.isLong() || node.isInt()) {
      return Optional.of(node.longValue());
    }

    if (node.isTextual()) {
      String rawValue = node.asText();
      if (!StringUtils.hasText(rawValue)) {
        return Optional.empty();
      }
      try {
        return Optional.of(Long.parseLong(rawValue.trim()));
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
