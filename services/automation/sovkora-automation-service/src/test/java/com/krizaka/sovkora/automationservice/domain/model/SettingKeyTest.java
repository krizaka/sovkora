package com.krizaka.sovkora.automationservice.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettingKeyTest {

  @Test
  @DisplayName("a stored value outside the hard bounds is brought back inside")
  void clamps() {
    SettingKey key = SettingKey.MAX_AUTOMATIONS_PER_OWNER;
    assertThat(key.clamp(0)).isEqualTo(1);
    assertThat(key.clamp(-5)).isEqualTo(1);
    assertThat(key.clamp(10)).isEqualTo(10);
    assertThat(key.clamp(1_000_000)).isEqualTo(200);
  }

  @Test
  @DisplayName("every default lies inside its own bounds, and keys are distinct")
  void defaultsAreInBounds() {
    for (SettingKey key : SettingKey.values()) {
      assertThat(key.clamp(key.defaultValue())).as(key.key()).isEqualTo(key.defaultValue());
    }
    assertThat(java.util.Arrays.stream(SettingKey.values()).map(SettingKey::key).distinct().count())
        .isEqualTo(SettingKey.values().length);
  }
}
