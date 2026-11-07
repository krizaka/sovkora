package com.krizaka.sovkora.core.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for core domain records introduced by the 4-Tier Validation Matrix.
 *
 * <p>Validates constructor invariants, self-validation, defensive copies, and boundary conditions
 * per ERR-106 / ERR-135.
 */
class ValidationDomainRecordsTest {

  // ── ValidationStepType ───────────────────────────────────────────────────

  @Nested
  @DisplayName("ValidationStepType")
  class ValidationStepTypeTests {

    @Test
    @DisplayName("All four tiers exist with correct default ordering")
    void allTiersExistWithCorrectOrder() {
      assertThat(ValidationStepType.values()).hasSize(4);
      assertThat(ValidationStepType.STRUCTURAL_A.defaultOrder()).isEqualTo(1);
      assertThat(ValidationStepType.SANDBOX_B.defaultOrder()).isEqualTo(2);
      assertThat(ValidationStepType.SEMANTIC_C.defaultOrder()).isEqualTo(3);
      assertThat(ValidationStepType.TDR_D.defaultOrder()).isEqualTo(4);
    }

    @Test
    @DisplayName("valueOf resolves each tier by name")
    void valueOfResolvesAllTiers() {
      assertThat(ValidationStepType.valueOf("STRUCTURAL_A"))
          .isEqualTo(ValidationStepType.STRUCTURAL_A);
      assertThat(ValidationStepType.valueOf("SANDBOX_B")).isEqualTo(ValidationStepType.SANDBOX_B);
      assertThat(ValidationStepType.valueOf("SEMANTIC_C")).isEqualTo(ValidationStepType.SEMANTIC_C);
      assertThat(ValidationStepType.valueOf("TDR_D")).isEqualTo(ValidationStepType.TDR_D);
    }

    @Test
    @DisplayName("Invalid tier name throws IllegalArgumentException")
    void invalidTierThrows() {
      assertThatThrownBy(() -> ValidationStepType.valueOf("TIER_X"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // ── ValidationPipelineConfiguration ──────────────────────────────────────

  @Nested
  @DisplayName("ValidationPipelineConfiguration")
  class ValidationPipelineConfigurationTests {

    @Test
    @DisplayName("Valid construction with all fields")
    void validConstruction() {
      UUID id = UUID.randomUUID();
      Map<String, Object> payload = Map.of("schemaStrict", true);
      var config =
          new ValidationPipelineConfiguration(
              id, ValidationStepType.STRUCTURAL_A, true, 1, payload);

      assertThat(config.id()).isEqualTo(id);
      assertThat(config.stepType()).isEqualTo(ValidationStepType.STRUCTURAL_A);
      assertThat(config.enabled()).isTrue();
      assertThat(config.executionOrder()).isEqualTo(1);
      assertThat(config.configurationPayload()).containsEntry("schemaStrict", true);
    }

    @Test
    @DisplayName("Null id throws NullPointerException")
    void nullIdThrows() {
      assertThatThrownBy(
              () ->
                  new ValidationPipelineConfiguration(
                      null, ValidationStepType.TDR_D, true, 1, Map.of()))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("id");
    }

    @Test
    @DisplayName("Null stepType throws NullPointerException")
    void nullStepTypeThrows() {
      UUID randomId = UUID.randomUUID();
      assertThatThrownBy(
              () -> new ValidationPipelineConfiguration(randomId, null, true, 1, Map.of()))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("stepType");
    }

    @Test
    @DisplayName("Negative executionOrder throws IllegalArgumentException")
    void negativeOrderThrows() {
      UUID randomId = UUID.randomUUID();
      assertThatThrownBy(
              () ->
                  new ValidationPipelineConfiguration(
                      randomId, ValidationStepType.STRUCTURAL_A, true, -1, Map.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("executionOrder");
    }

    @Test
    @DisplayName("Zero executionOrder is accepted")
    void zeroOrderAccepted() {
      var config =
          new ValidationPipelineConfiguration(
              UUID.randomUUID(), ValidationStepType.SEMANTIC_C, false, 0, Map.of());
      assertThat(config.executionOrder()).isZero();
    }

    @Test
    @DisplayName("Null payload defaults to empty map")
    void nullPayloadDefaultsToEmptyMap() {
      var config =
          new ValidationPipelineConfiguration(
              UUID.randomUUID(), ValidationStepType.SANDBOX_B, true, 2, null);
      assertThat(config.configurationPayload()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Payload is defensively copied (immutable)")
    void payloadIsDefensivelyCopied() {
      var mutablePayload = new java.util.HashMap<String, Object>();
      mutablePayload.put("key", "value");
      var config =
          new ValidationPipelineConfiguration(
              UUID.randomUUID(), ValidationStepType.TDR_D, true, 4, mutablePayload);

      // Modify original should NOT affect record
      mutablePayload.put("newKey", "newValue");
      assertThat(config.configurationPayload()).doesNotContainKey("newKey");

      // Record payload should be unmodifiable
      var payload = config.configurationPayload();
      assertThatThrownBy(() -> payload.put("x", "y"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Equality based on all fields")
    void equalityBasedOnAllFields() {
      UUID id = UUID.randomUUID();
      var a = new ValidationPipelineConfiguration(id, ValidationStepType.TDR_D, true, 4, Map.of());
      var b = new ValidationPipelineConfiguration(id, ValidationStepType.TDR_D, true, 4, Map.of());
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
  }

  // ── AssertionContract ───────────────────────────────────────────────────

  @Nested
  @DisplayName("AssertionContract")
  class AssertionContractTests {

    @Test
    @DisplayName("Valid construction with assertions")
    void validConstruction() {
      var contract = new AssertionContract("schema-1", List.of("must contain code"), "2026-06-03");
      assertThat(contract.schemaName()).isEqualTo("schema-1");
      assertThat(contract.assertions()).containsExactly("must contain code");
      assertThat(contract.generatedAt()).isEqualTo("2026-06-03");
    }

    @Test
    @DisplayName("Null schemaName throws NullPointerException")
    void nullSchemaNameThrows() {
      assertThatThrownBy(() -> new AssertionContract(null, List.of(), "now"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("schemaName");
    }

    @Test
    @DisplayName("Null generatedAt throws NullPointerException")
    void nullGeneratedAtThrows() {
      assertThatThrownBy(() -> new AssertionContract("schema", List.of(), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("generatedAt");
    }

    @Test
    @DisplayName("Null assertions defaults to empty list")
    void nullAssertionsDefaultsToEmptyList() {
      var contract = new AssertionContract("schema", null, "now");
      assertThat(contract.assertions()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Assertions list is defensively copied (immutable)")
    void assertionsDefensivelyCopied() {
      var mutable = new java.util.ArrayList<>(List.of("a", "b"));
      var contract = new AssertionContract("schema", mutable, "now");
      mutable.add("c");
      assertThat(contract.assertions()).hasSize(2).doesNotContain("c");

      var assertions = contract.assertions();
      assertThatThrownBy(() -> assertions.add("x"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("empty() returns contract with no assertions")
    void emptyContract() {
      var empty = AssertionContract.empty();
      assertThat(empty.schemaName()).isEqualTo("none");
      assertThat(empty.assertions()).isEmpty();
      assertThat(empty.hasAssertions()).isFalse();
    }

    @Test
    @DisplayName("hasAssertions returns true when non-empty")
    void hasAssertionsWhenNonEmpty() {
      var contract = new AssertionContract("s", List.of("check"), "now");
      assertThat(contract.hasAssertions()).isTrue();
    }

    @Test
    @DisplayName("hasAssertions returns false when empty")
    void hasAssertionsWhenEmpty() {
      var contract = new AssertionContract("s", List.of(), "now");
      assertThat(contract.hasAssertions()).isFalse();
    }
  }
}
