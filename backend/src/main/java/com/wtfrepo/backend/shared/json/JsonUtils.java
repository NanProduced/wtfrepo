package com.wtfrepo.backend.shared.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Shared JSON helper to centralize serialization/deserialization behavior.
 */
@Component
public class JsonUtils {

  private final ObjectMapper objectMapper;

  public JsonUtils(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Serializes value into JSON string. */
  public String toJson(Object value) throws JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }

  /** Deserializes JSON into generic Object tree. */
  public Object toObject(String json) throws JsonProcessingException {
    return objectMapper.readValue(json, new TypeReference<Object>() {});
  }

  /** Exposes ObjectMapper only for rare advanced scenarios. */
  public ObjectMapper mapper() {
    return objectMapper;
  }
}

