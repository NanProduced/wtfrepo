package com.wtfrepo.backend.economy.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Lightweight payload reader for economy outbox handlers.
 *
 * <p>All methods are fail-safe by default and return empty optionals on malformed payload.
 */
@Component
public class EconomyOutboxPayloadReader {

  private final ObjectMapper objectMapper;

  public EconomyOutboxPayloadReader(ObjectMapper objectMapper) {
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

  public Optional<LocalDate> readLocalDateField(String payload, String fieldName) {
    return readTextField(payload, fieldName)
        .flatMap(
            value -> {
              try {
                return Optional.of(LocalDate.parse(value));
              } catch (DateTimeParseException ignored) {
                return Optional.empty();
              }
            });
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
