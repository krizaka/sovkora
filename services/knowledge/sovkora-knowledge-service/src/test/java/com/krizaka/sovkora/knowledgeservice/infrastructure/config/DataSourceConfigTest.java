package com.krizaka.sovkora.knowledgeservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataSourceConfigTest {

  @Test
  @DisplayName("The pool is built from the knowledge properties, never from spring.datasource")
  void poolBuiltFromKnowledgeProperties() {
    var properties =
        new KnowledgeDataSourceProperties(
            "jdbc:postgresql://localhost:5432/orazaka_knowledge_db", "orazaka_knowledge", "secret");

    try (HikariDataSource dataSource =
        (HikariDataSource) new DataSourceConfig().knowledgeDataSource(properties)) {
      assertThat(dataSource.getJdbcUrl()).isEqualTo(properties.url());
      assertThat(dataSource.getUsername()).isEqualTo("orazaka_knowledge");
      assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
    }
  }
}
