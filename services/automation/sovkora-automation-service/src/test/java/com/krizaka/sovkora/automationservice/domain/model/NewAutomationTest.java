package com.krizaka.sovkora.automationservice.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NewAutomationTest {

  @Test
  @DisplayName("a valid draft is trimmed, enabled by default, with empty inputs by default")
  void defaults() {
    NewAutomation draft = new NewAutomation("  Lease check  ", "compliance-check", null, null);
    assertThat(draft.name()).isEqualTo("Lease check");
    assertThat(draft.enabled()).isTrue();
    assertThat(draft.inputs()).isEmpty();
  }

  @Test
  @DisplayName("the inputs are copied: a later change of the caller's map cannot reach the draft")
  void inputsAreCopied() {
    Map<String, Object> mutable = new java.util.HashMap<>(Map.of("a", 1));
    NewAutomation draft = new NewAutomation("n", "k", mutable, true);
    mutable.put("b", 2);
    assertThat(draft.inputs()).containsOnlyKeys("a");
    assertThatThrownBy(() -> draft.inputs().put("c", 3))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("a blank name is refused")
  void blankName(String name) {
    assertThatThrownBy(() -> new NewAutomation(name, "k", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  @DisplayName("a missing or over-long name is refused")
  void nameBounds() {
    assertThatThrownBy(() -> new NewAutomation(null, "k", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new NewAutomation("x".repeat(NewAutomation.NAME_MAX + 1), "k", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new NewAutomation("x".repeat(NewAutomation.NAME_MAX), "k", null, null).name())
        .hasSize(NewAutomation.NAME_MAX);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Upper", "has space", "-leading", "a/b", "a:b"})
  @DisplayName("a Studio key that is not a key is refused")
  void studioKey(String key) {
    assertThatThrownBy(() -> new NewAutomation("n", key, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("studioKey");
  }

  @Test
  @DisplayName("a missing Studio key is refused")
  void missingStudioKey() {
    assertThatThrownBy(() -> new NewAutomation("n", null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
