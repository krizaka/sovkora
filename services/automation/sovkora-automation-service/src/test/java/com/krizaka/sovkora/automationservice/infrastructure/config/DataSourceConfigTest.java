package com.krizaka.sovkora.automationservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataSourceConfigTest {

  @Test
  @DisplayName("The pool is built from the automation properties, never from spring.datasource")
  void poolBuiltFromAutomationProperties() {
    var properties =
        new AutomationDataSourceProperties(
            "jdbc:postgresql://localhost:5432/orazaka_automation_db",
            "orazaka_automation",
            "secret",
            5);

    try (HikariDataSource dataSource =
        (HikariDataSource) new DataSourceConfig().automationDataSource(properties)) {
      assertThat(dataSource.getJdbcUrl()).isEqualTo(properties.url());
      assertThat(dataSource.getUsername()).isEqualTo("orazaka_automation");
      assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
    }
  }

  @Test
  @DisplayName("The pool size is the wiring's: one connection on dev")
  void poolSizeFollowsTheWiring() {
    var properties =
        new AutomationDataSourceProperties(
            "jdbc:postgresql://localhost:5432/orazaka_automation_db", "u", "p", 1);
    try (HikariDataSource dataSource =
        (HikariDataSource) new DataSourceConfig().automationDataSource(properties)) {
      assertThat(dataSource.getMaximumPoolSize()).isEqualTo(1);
      assertThat(dataSource.getMinimumIdle()).isEqualTo(1);
    }
  }
}
