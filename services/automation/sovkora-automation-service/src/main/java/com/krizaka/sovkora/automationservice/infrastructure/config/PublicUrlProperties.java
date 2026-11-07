package com.krizaka.sovkora.automationservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The address senders call to reach a webhook trigger ({@code orazaka.automation-service.public}):
 * the edge's public address, so the owner is shown a complete URL.
 *
 * @param baseUrl for example {@code https://dev.sovkora.com/edge}, without a trailing slash
 */
@ConfigurationProperties(prefix = "orazaka.automation-service.public")
public record PublicUrlProperties(@DefaultValue("http://localhost:8088") String baseUrl) {

  /** Compact canonical constructor enforcing the wiring's invariants (ERR-106). */
  public PublicUrlProperties {
    baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  /**
   * The URL a sender calls for a trigger.
   *
   * @param triggerId the trigger
   * @return the full URL
   */
  public String webhookUrl(java.util.UUID triggerId) {
    return baseUrl + "/hooks/" + triggerId;
  }
}
