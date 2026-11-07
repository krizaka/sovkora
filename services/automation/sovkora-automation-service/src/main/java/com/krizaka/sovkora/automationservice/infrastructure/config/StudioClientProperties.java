package com.krizaka.sovkora.automationservice.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the automation service starts Studio runs ({@code orazaka.automation-service.studio}):
 * wiring only. The runs are started over the Studio service's own REST surface, as the automation's
 * owner (ADR-084 §context).
 *
 * @param baseUrl the Studio service
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for an answer
 */
@ConfigurationProperties(prefix = "orazaka.automation-service.studio")
public record StudioClientProperties(
    @DefaultValue("http://localhost:8096") URI baseUrl,
    @DefaultValue("500ms") Duration connectTimeout,
    @DefaultValue("10s") Duration readTimeout) {

  /** Compact canonical constructor enforcing the wiring's invariants (ERR-106). */
  public StudioClientProperties {
    Objects.requireNonNull(baseUrl, "the Studio service url is required");
  }
}
