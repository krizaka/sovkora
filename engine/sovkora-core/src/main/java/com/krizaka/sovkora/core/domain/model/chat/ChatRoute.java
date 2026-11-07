package com.krizaka.sovkora.core.domain.model.chat;

/**
 * Which model answers a turn: the platform's own, or a commercial provider on the user's key.
 *
 * @param provider the provider name, ignored when {@code apiKey} is {@code null}
 * @param apiKey the user's decrypted key, or {@code null} to use the platform's model
 */
public record ChatRoute(String provider, String apiKey) {

  /**
   * The platform's own model.
   *
   * @return the route
   */
  public static ChatRoute platform() {
    return new ChatRoute(null, null);
  }

  /**
   * A provider on the user's own key.
   *
   * @param provider the provider name
   * @param apiKey the decrypted key
   * @return the route
   */
  public static ChatRoute userKey(String provider, String apiKey) {
    return new ChatRoute(provider, apiKey);
  }

  /**
   * Whether the route uses the platform's model.
   *
   * @return {@code true} when there is no user key
   */
  public boolean isPlatform() {
    return apiKey == null;
  }
}
