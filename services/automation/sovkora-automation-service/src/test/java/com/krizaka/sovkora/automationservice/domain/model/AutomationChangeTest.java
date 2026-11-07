package com.krizaka.sovkora.automationservice.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutomationChangeTest {

  @Test
  @DisplayName("a change with no field says nothing")
  void empty() {
    assertThat(new AutomationChange(null, null, null).isEmpty()).isTrue();
    assertThat(new AutomationChange(null, null, false).isEmpty()).isFalse();
    assertThat(new AutomationChange("n", null, null).isEmpty()).isFalse();
    assertThat(new AutomationChange(null, Map.of(), null).isEmpty()).isFalse();
  }

  @Test
  @DisplayName("a new name is validated like a created one, and trimmed")
  void nameValidated() {
    assertThat(new AutomationChange("  new  ", null, null).name()).isEqualTo("new");
    assertThatThrownBy(() -> new AutomationChange("  ", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
