package com.krizaka.sovkora.automationservice.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The Standard Webhooks signature scheme (https://www.standardwebhooks.com): HMAC-SHA256 over
 * {@code <webhook-id>.<webhook-timestamp>.<body>}, sent as {@code v1,<base64>} in {@code
 * webhook-signature} — a space-separated list when a secret is being rotated.
 *
 * <p>Dependency-free pure functions (AGENTS.md ERR-127, exception b): no state, no collaborator.
 */
public final class WebhookSignature {

  private static final String HMAC = "HmacSHA256";
  private static final String VERSION = "v1,";

  private WebhookSignature() {}

  /**
   * Signs a delivery.
   *
   * @param key the HMAC key
   * @param webhookId the {@code webhook-id}
   * @param timestamp the {@code webhook-timestamp}, as sent
   * @param body the raw body
   * @return {@code v1,<base64>}
   */
  public static String sign(byte[] key, String webhookId, String timestamp, byte[] body) {
    return VERSION + Base64.getEncoder().encodeToString(mac(key, webhookId, timestamp, body));
  }

  /**
   * Whether any of the signatures of the header is the one this key makes for the delivery. The
   * comparison is constant-time and every candidate is compared, so the time taken does not say
   * which one matched or where it differed.
   *
   * @param key the HMAC key
   * @param header the value of {@code webhook-signature}
   * @param webhookId the {@code webhook-id}
   * @param timestamp the {@code webhook-timestamp}, as sent
   * @param body the raw body
   * @return true when one signature matches
   */
  public static boolean verify(
      byte[] key, String header, String webhookId, String timestamp, byte[] body) {
    byte[] expected = mac(key, webhookId, timestamp, body);
    boolean matched = false;
    for (String candidate : header == null ? new String[0] : header.strip().split("\\s+")) {
      if (!candidate.startsWith(VERSION)) {
        continue;
      }
      byte[] given;
      try {
        given = Base64.getDecoder().decode(candidate.substring(VERSION.length()));
      } catch (IllegalArgumentException malformed) {
        continue;
      }
      matched |= MessageDigest.isEqual(expected, given);
    }
    return matched;
  }

  private static byte[] mac(byte[] key, String webhookId, String timestamp, byte[] body) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(key, HMAC));
      mac.update((webhookId + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
      return mac.doFinal(body);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("cannot compute the webhook signature", e);
    }
  }
}
