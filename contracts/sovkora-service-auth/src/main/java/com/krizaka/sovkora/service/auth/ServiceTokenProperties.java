package com.krizaka.sovkora.service.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The key Sovkora's own service tokens are signed and verified with ({@code
 * sovkora.security.service-token}, bound from {@code AUTH_SERVICE_TOKEN_SECRET}).
 *
 * <p>It is not the session key: whoever holds the session key ({@code IDENTITY_JWT_SECRET}) can
 * mint a session for anyone, and before this key existed it could also mint {@code SERVICE}. A
 * service that verifies tokens refuses to start without it, so a missing variable can never fall
 * back to a value committed in a file.
 *
 * @param secret the HS256 key, at least 32 characters (256 bits), never the session key
 */
@ConfigurationProperties(prefix = "sovkora.security.service-token")
public record ServiceTokenProperties(String secret) {

  /** The minimum key length, in characters, for a 256-bit HS256 key. */
  public static final int MINIMUM_SECRET_LENGTH = 32;

  /**
   * Refuses a missing or short key.
   *
   * @throws IllegalArgumentException when the key is absent, blank or shorter than 32 characters
   */
  public ServiceTokenProperties {
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException(
          "AUTH_SERVICE_TOKEN_SECRET is not set (sovkora.security.service-token.secret): a"
              + " Sovkora service does not start without the key its service tokens are signed"
              + " with");
    }
    if (secret.length() < MINIMUM_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          "AUTH_SERVICE_TOKEN_SECRET must be at least "
              + MINIMUM_SECRET_LENGTH
              + " characters (256-bit HS256)");
    }
  }

  /**
   * Hides the key from logs, actuator output and exception messages.
   *
   * @return a description that never contains the key
   */
  @Override
  public String toString() {
    return "ServiceTokenProperties[secret=<redacted>]";
  }
}
