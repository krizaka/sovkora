package com.krizaka.sovkora.knowledgeservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnowledgeDataSourcePropertiesTest {

  @Test
  @DisplayName("Valid knowledge datasource wiring is accepted")
  void validWiring() {
    var properties =
        new KnowledgeDataSourceProperties(
            "jdbc:postgresql://localhost:5432/orazaka_knowledge_db", "orazaka_knowledge", "secret");
    assertThat(properties.url()).contains("orazaka_knowledge_db");
    assertThat(properties.username()).isEqualTo("orazaka_knowledge");
  }

  @Test
  @DisplayName("Missing values and non-postgres URLs are rejected")
  void invalidWiringRejected() {
    assertThatNullPointerException()
        .isThrownBy(() -> new KnowledgeDataSourceProperties(null, "u", "p"));
    assertThatNullPointerException()
        .isThrownBy(() -> new KnowledgeDataSourceProperties("jdbc:postgresql://h/db", null, "p"));
    assertThatNullPointerException()
        .isThrownBy(() -> new KnowledgeDataSourceProperties("jdbc:postgresql://h/db", "u", null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new KnowledgeDataSourceProperties("jdbc:mysql://x/db", "u", "p"));
  }
}
