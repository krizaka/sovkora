package com.krizaka.sovkora.tools.infrastructure.law;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeConfigGroundedResearchSettingsTest {

  private final Map<String, String> rows = new HashMap<>();
  private final RuntimeConfigGroundedResearchSettings settings =
      new RuntimeConfigGroundedResearchSettings(
          new RuntimeConfigProvider() {
            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
              return Boolean.parseBoolean(rows.getOrDefault(key, String.valueOf(defaultValue)));
            }

            @Override
            public int getInt(String key, int defaultValue) {
              return Integer.parseInt(rows.getOrDefault(key, String.valueOf(defaultValue)));
            }

            @Override
            public String getString(String key, String defaultValue) {
              return rows.getOrDefault(key, defaultValue);
            }
          });

  @Test
  void a_lecture_has_no_credits_until_its_row_says_so() {
    assertThat(settings.lectureMaxCredits()).isZero();

    rows.put("law.lecture.max-credits", "250");

    assertThat(settings.lectureMaxCredits()).isEqualTo(250);
  }

  @Test
  void the_lecture_switch_is_off_without_its_row() {
    assertThat(settings.lectureEnabled()).isFalse();
  }
}
