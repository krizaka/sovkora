package com.krizaka.sovkora.knowledgeservice.infrastructure.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The knowledge service's own datasource wiring ({@code orazaka.knowledge-service.datasource}),
 * bound from {@code KNOWLEDGE_DB_*} only. A dedicated prefix (instead of {@code spring.datasource})
 * keeps the shared local {@code .env} — which exports {@code SPRING_DATASOURCE_*} for the app
 * database — from hijacking this service's connection through Spring's env-var precedence over
 * yaml.
 *
 * @param url the JDBC URL of {@code orazaka_knowledge_db}
 * @param username the service's own database role
 * @param password the role's password
 */
@ConfigurationProperties(prefix = "orazaka.knowledge-service.datasource")
public record KnowledgeDataSourceProperties(String url, String username, String password) {

  public KnowledgeDataSourceProperties {
    Objects.requireNonNull(url, "knowledge datasource url is required");
    Objects.requireNonNull(username, "knowledge datasource username is required");
    Objects.requireNonNull(password, "knowledge datasource password is required");
    if (!url.startsWith("jdbc:postgresql:")) {
      throw new IllegalArgumentException("knowledge datasource url must be a postgresql JDBC url");
    }
  }
}
