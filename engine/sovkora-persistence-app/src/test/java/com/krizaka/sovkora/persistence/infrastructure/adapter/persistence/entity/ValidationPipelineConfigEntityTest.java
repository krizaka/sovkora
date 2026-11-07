package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ValidationPipelineConfigEntity}. */
class ValidationPipelineConfigEntityTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Test
  @DisplayName("Default values are set on construction")
  void defaultValues() {
    var entity = new ValidationPipelineConfigEntity();
    assertTrue(entity.getIsEnabled());
    assertEquals(0, entity.getExecutionOrder());
    assertNotNull(entity.getConfigurationPayload());
    assertTrue(entity.getConfigurationPayload().isEmpty());
    assertNotNull(entity.getCreatedAt());
    assertNotNull(entity.getUpdatedAt());
  }

  @Test
  @DisplayName("Set and get id")
  void setAndGetId() {
    var entity = new ValidationPipelineConfigEntity();
    UUID id = UUID.randomUUID();
    entity.setId(id);
    assertEquals(id, entity.getId());
  }

  @Test
  @DisplayName("Set and get stepType as String")
  void setAndGetStepType() {
    var entity = new ValidationPipelineConfigEntity();
    entity.setStepType("TDR_D");
    assertEquals("TDR_D", entity.getStepType());
  }

  @Test
  @DisplayName("Set and get isEnabled")
  void setAndGetIsEnabled() {
    var entity = new ValidationPipelineConfigEntity();
    entity.setIsEnabled(false);
    assertFalse(entity.getIsEnabled());
  }

  @Test
  @DisplayName("Set and get executionOrder")
  void setAndGetExecutionOrder() {
    var entity = new ValidationPipelineConfigEntity();
    entity.setExecutionOrder(3);
    assertEquals(3, entity.getExecutionOrder());
  }

  @Test
  @DisplayName("Set and get configurationPayload")
  void setAndGetConfigurationPayload() {
    var entity = new ValidationPipelineConfigEntity();
    Map<String, Object> payload = Map.of("schemaStrict", true, "timeout", 30);
    entity.setConfigurationPayload(payload);
    assertEquals(true, entity.getConfigurationPayload().get("schemaStrict"));
    assertEquals(30, entity.getConfigurationPayload().get("timeout"));
  }

  @Test
  @DisplayName("Set and get createdAt")
  void setAndGetCreatedAt() {
    var entity = new ValidationPipelineConfigEntity();
    Instant now = Instant.now(FIXED_CLOCK);
    entity.setCreatedAt(now);
    assertEquals(now, entity.getCreatedAt());
  }

  @Test
  @DisplayName("Set and get updatedAt")
  void setAndGetUpdatedAt() {
    var entity = new ValidationPipelineConfigEntity();
    Instant now = Instant.now(FIXED_CLOCK);
    entity.setUpdatedAt(now);
    assertEquals(now, entity.getUpdatedAt());
  }

  @Test
  @DisplayName("All step type strings can be assigned")
  void allStepTypeStringsAssignable() {
    var entity = new ValidationPipelineConfigEntity();
    for (String type : new String[] {"STRUCTURAL_A", "SANDBOX_B", "SEMANTIC_C", "TDR_D"}) {
      entity.setStepType(type);
      assertEquals(type, entity.getStepType());
    }
  }

  @Test
  @DisplayName("Full entity population round-trip")
  void fullEntityPopulation() {
    var entity = new ValidationPipelineConfigEntity();
    UUID id = UUID.randomUUID();
    Instant now = Instant.now(FIXED_CLOCK);

    entity.setId(id);
    entity.setStepType("SEMANTIC_C");
    entity.setIsEnabled(true);
    entity.setExecutionOrder(3);
    entity.setConfigurationPayload(Map.of("debateTemperature", 0.0));
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);

    assertEquals(id, entity.getId());
    assertEquals("SEMANTIC_C", entity.getStepType());
    assertTrue(entity.getIsEnabled());
    assertEquals(3, entity.getExecutionOrder());
    assertEquals(0.0, entity.getConfigurationPayload().get("debateTemperature"));
    assertEquals(now, entity.getCreatedAt());
    assertEquals(now, entity.getUpdatedAt());
  }
}
