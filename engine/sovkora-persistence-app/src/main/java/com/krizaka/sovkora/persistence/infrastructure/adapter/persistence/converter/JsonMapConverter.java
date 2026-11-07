package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * JPA Attribute Converter that maps a Java {@link Map} to a JSON string column in the database.
 * Resides in the infrastructure persistence converter namespace per ERR-109.
 */
@Converter(autoApply = false)
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

  private static final Logger logger = LoggerFactory.getLogger(JsonMapConverter.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(Map<String, Object> attribute) {
    if (attribute == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(attribute);
    } catch (JacksonException e) {
      logger.error("Failed to serialize Map to JSON string", e);
      throw new IllegalArgumentException("Failed to serialize Map to JSON string", e);
    }
  }

  @Override
  public Map<String, Object> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return new HashMap<>();
    }
    try {
      return objectMapper.readValue(dbData, new TypeReference<Map<String, Object>>() {});
    } catch (JacksonException e) {
      logger.warn(
          "Failed to parse JSON string to Map - returning empty fallback. Cause: {}",
          e.getMessage());
      return new HashMap<>();
    }
  }
}
