package com.krizaka.sovkora.engine.interceptors.governance;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which environment this deployment's provider spend is counted in. Infrastructure wiring only: the
 * caps, the prices and the thresholds are rows.
 *
 * @param environment {@code local}, {@code dev} or {@code prod}; a deployment names its own
 */
@ConfigurationProperties(prefix = "orazaka.spend")
public record SpendCapProperties(String environment) {

  public SpendCapProperties {
    if (environment == null || environment.isBlank()) {
      throw new IllegalArgumentException(
          "the spend environment (AI_SPEND_ENVIRONMENT) must name the environment this deployment's"
              + " spend is counted in");
    }
  }
}
