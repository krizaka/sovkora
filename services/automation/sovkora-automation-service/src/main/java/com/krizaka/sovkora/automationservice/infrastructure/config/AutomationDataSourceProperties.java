package com.krizaka.sovkora.automationservice.infrastructure.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The automation service's own datasource wiring ({@code orazaka.automation-service.datasource}),
 * bound from {@code AUTOMATION_DB_*} only. A dedicated prefix (instead of {@code
 * spring.datasource}) keeps the shared local {@code .env} — which exports {@code
 * SPRING_DATASOURCE_*} for the app database — from hijacking this service's connection through
 * Spring's env-var precedence over yaml.
 *
 * @param url the JDBC URL of {@code orazaka_automation_db}
 * @param username the service's own database role
 * @param password the role's password
 * @param maxPoolSize the most connections the service holds (1 on dev: the cluster has 22 for
 *     everyone)
 */
@ConfigurationProperties(prefix = "orazaka.automation-service.datasource")
public record AutomationDataSourceProperties(
    String url, String username, String password, @DefaultValue("5") int maxPoolSize) {

  public AutomationDataSourceProperties {
    Objects.requireNonNull(url, "automation datasource url is required");
    Objects.requireNonNull(username, "automation datasource username is required");
    Objects.requireNonNull(password, "automation datasource password is required");
    if (maxPoolSize < 1) {
      throw new IllegalArgumentException("automation datasource max-pool-size must be at least 1");
    }
    if (!url.startsWith("jdbc:postgresql:")) {
      throw new IllegalArgumentException("automation datasource url must be a postgresql JDBC url");
    }
  }
}
