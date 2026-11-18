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
 * @param serviceSecret the identity session key ({@code IDENTITY_JWT_SECRET}) the SERVICE token of
 *     the exchange is signed with: the users service verifies it with that key. Required, at least
 *     32 characters: the edge does not start without it
 * @param apiKeyEnvironment the environment this edge serves, {@code dev} or {@code prod} ({@code
 *     AUTH_API_KEY_ENVIRONMENT}, krizaka-internal#2132): a key of another environment is refused
 */
@ConfigurationProperties(prefix = "orazaka.edge.identity")
public record EdgeIdentityProperties(URI baseUrl, String serviceSecret, String apiKeyEnvironment) {

  /** The minimum key length, in characters, for a 256-bit HS256 key. */
  public static final int MINIMUM_SECRET_LENGTH = 32;

  @ConstructorBinding
  public EdgeIdentityProperties {
    Objects.requireNonNull(baseUrl, "identity base-url is required");
    if (baseUrl.getHost() == null) {
      throw new IllegalArgumentException("identity base-url must be absolute: " + baseUrl);
    }
    if (serviceSecret == null || serviceSecret.length() < MINIMUM_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          "IDENTITY_JWT_SECRET is not set (orazaka.edge.identity.service-secret) or shorter than "
              + MINIMUM_SECRET_LENGTH
              + " characters: the edge does not start without the key its API-key exchange is"
              + " signed with");
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
