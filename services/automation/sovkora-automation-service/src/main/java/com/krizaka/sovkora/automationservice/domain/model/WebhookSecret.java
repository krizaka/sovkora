package com.krizaka.sovkora.automationservice.domain.model;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * The secret of one webhook trigger: 32 random bytes in the Standard Webhooks form {@code
 * whsec_<base64>}. The HMAC key is the decoded bytes. It is a value that knows how to hide itself:
 * {@link #toString()} never prints it.
 */
public final class WebhookSecret {

  /** The prefix of the transport form. */
  public static final String PREFIX = "whsec_";

  /** The length of the key, in bytes. */
  public static final int KEY_BYTES = 32;

  private static final int HINT_CHARS = 4;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final String value;

  private WebhookSecret(String value) {
    this.value = value;
  }

  /**
   * A fresh secret.
   *
   * @return 32 random bytes
   */
  public static WebhookSecret generate() {
    byte[] key = new byte[KEY_BYTES];
    RANDOM.nextBytes(key);
    return new WebhookSecret(PREFIX + Base64.getEncoder().encodeToString(key));
  }

  /**
   * Reads a secret in its transport form.
   *
   * @param value {@code whsec_} and the base64 of the key
   * @return the secret
   * @throws IllegalArgumentException when it is not one
   */
  public static WebhookSecret of(String value) {
    if (value == null || !value.startsWith(PREFIX)) {
      throw new IllegalArgumentException("not a webhook secret");
    }
    byte[] key;
    try {
      key = Base64.getDecoder().decode(value.substring(PREFIX.length()));
    } catch (IllegalArgumentException malformed) {
      throw new IllegalArgumentException("not a webhook secret");
    }
    if (key.length != KEY_BYTES) {
      throw new IllegalArgumentException("not a webhook secret");
    }
    return new WebhookSecret(value);
  }

  /**
   * The transport form, shown once to the owner.
   *
   * @return {@code whsec_…}
   */
  public String reveal() {
    return value;
  }

  /**
   * The HMAC key.
   *
   * @return the 32 decoded bytes
   */
  public byte[] key() {
    return Base64.getDecoder().decode(value.substring(PREFIX.length()));
  }

  /**
   * The last characters, enough to tell two secrets apart and nothing to guess from.
   *
   * @return four characters
   */
  public String hint() {
    return value.substring(value.length() - HINT_CHARS);
  }

  @Override
  public String toString() {
    return "WebhookSecret[<redacted>]";
  }
}
