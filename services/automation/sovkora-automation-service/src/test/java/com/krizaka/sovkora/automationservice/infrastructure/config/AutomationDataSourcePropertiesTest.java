package com.krizaka.sovkora.automationservice.infrastructure.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutomationDataSourcePropertiesTest {

  @Test
  @DisplayName("Valid automation datasource wiring is accepted")
  void validWiring() {
    var properties =
        new AutomationDataSourceProperties(
            "jdbc:postgresql://localhost:5432/orazaka_automation_db",
            "orazaka_automation",
            "secret",
            5);
    assertThat(properties.url()).contains("orazaka_automation_db");
    assertThat(properties.username()).isEqualTo("orazaka_automation");
  }

  @Test
  @DisplayName("Missing values and non-postgres URLs are rejected")
  void invalidWiringRejected() {
    assertThatNullPointerException()
        .isThrownBy(() -> new AutomationDataSourceProperties(null, "u", "p", 5));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new AutomationDataSourceProperties(
                    "jdbc:postgresql://localhost:5432/db", null, "p", 5));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new AutomationDataSourceProperties(
                    "jdbc:postgresql://localhost:5432/db", "u", null, 5));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AutomationDataSourceProperties("jdbc:mysql://x/db", "u", "p", 5));
  }

  @Test
  @DisplayName("The pool holds at least one connection")
  void poolIsAtLeastOne() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new AutomationDataSourceProperties(
                    "jdbc:postgresql://localhost:5432/db", "u", "p", 0));
    assertThat(
            new AutomationDataSourceProperties("jdbc:postgresql://localhost:5432/db", "u", "p", 1)
                .maxPoolSize())
        .isEqualTo(1);
  }
}
