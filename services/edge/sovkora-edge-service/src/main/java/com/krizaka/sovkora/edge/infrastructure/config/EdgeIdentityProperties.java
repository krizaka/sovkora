package com.krizaka.sovkora.edge.infrastructure.config;

import java.net.URI;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Wiring of the identity service consumed by the edge ({@code orazaka.edge.identity}): the {@code
 * oz_} API-key exchange endpoint. Exchanges are deliberately uncached so key revocation takes
 * effect immediately.
 *
 * @param baseUrl the identity service base URL
 * @param serviceSecret the shared secret the SERVICE token of the exchange is signed with
 * @param apiKeyEnvironment the environment this edge serves, {@code dev} or {@code prod} ({@code
 *     AUTH_API_KEY_ENVIRONMENT}, krizaka-internal#2132): a key of another environment is refused
 */
@ConfigurationProperties(prefix = "orazaka.edge.identity")
public record EdgeIdentityProperties(URI baseUrl, String serviceSecret, String apiKeyEnvironment) {

  @ConstructorBinding
  public EdgeIdentityProperties {
    Objects.requireNonNull(baseUrl, "identity base-url is required");
    if (baseUrl.getHost() == null) {
      throw new IllegalArgumentException("identity base-url must be absolute: " + baseUrl);
    }
    if (apiKeyEnvironment == null || apiKeyEnvironment.isBlank()) {
      throw new IllegalArgumentException("identity api-key-environment is required: dev or prod");
    }
  }

  /** The wiring of a dev edge: keys that state no environment, or {@code env:dev}. */
  public EdgeIdentityProperties(URI baseUrl, String serviceSecret) {
    this(baseUrl, serviceSecret, "dev");
  }
}
