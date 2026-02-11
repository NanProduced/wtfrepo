package com.wtfrepo.backend.specimen.application.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wtfrepo.backend.shared.json.JsonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Shared JSON codec for specimen module payload persistence and response rendering.
 */
@Component
public class SpecimenJsonCodec {

  private final JsonUtils jsonUtils;

  public SpecimenJsonCodec(JsonUtils jsonUtils) {
    this.jsonUtils = jsonUtils;
  }

  public String write(Object value) {
    try {
      return jsonUtils.toJson(value);
    } catch (JsonProcessingException ex) {
      throw SpecimenExceptions.validation("invalid_json_payload");
    }
  }

  public Object readObject(String json) {
    if (!StringUtils.hasText(json)) {
      return null;
    }
    try {
      return jsonUtils.toObject(json);
    } catch (JsonProcessingException ex) {
      return null;
    }
  }
}
