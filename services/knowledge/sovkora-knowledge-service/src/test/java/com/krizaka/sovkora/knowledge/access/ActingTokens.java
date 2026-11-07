package com.krizaka.sovkora.knowledge.access;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mints the tokens the core would send, for the access tests: a service token ({@code roles:
 * [SERVICE]}) that may carry the {@code act} claim — the verified principal the call is made for —
 * and the forgeries a hostile caller could try (another secret, no signature, a payload edited
 * after signing).
 */
final class ActingTokens {

  static final String SECRET = "sovkora-local-dev-identity-jwt-secret-256bit!";

  private ActingTokens() {}

  /** A service token acting for {@code principalJson}, or for nobody when it is {@code null}. */
  static String service(String secret, String principalJson) {
    return sign(secret, payload(principalJson));
  }

  /** The principal the core sends for an ordinary employee or HR member of a tenant. */
  static String principal(String tenant, String subject, String... roles) {
    StringBuilder list = new StringBuilder();
    for (String role : roles) {
      list.append(list.isEmpty() ? "" : ",").append('"').append(role).append('"');
    }
    return "{\"sub\":\"%s\",\"tenant\":\"%s\",\"roles\":[%s],\"groups\":[]}"
        .formatted(subject, tenant, list);
  }

  static String actingFor(String tenant, String subject, String... roles) {
    return service(SECRET, principal(tenant, subject, roles));
  }

  /** Signed, then its payload replaced: the signature no longer matches. */
  static String tampered(String validToken, String newPrincipalJson) {
    String[] parts = validToken.split("\\.");
    return parts[0]
        + "."
        + encode(payload(newPrincipalJson).getBytes(StandardCharsets.UTF_8))
        + "."
        + parts[2];
  }

  /** {@code alg: none}: no signature at all. */
  static String unsigned(String principalJson) {
    return encode("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))
        + "."
        + encode(payload(principalJson).getBytes(StandardCharsets.UTF_8))
        + ".";
  }

  private static String payload(String principalJson) {
    Instant now = Instant.now();
    String act = principalJson == null ? "" : ",\"act\":" + principalJson;
    return "{\"sub\":\"sovkora-engine-core\",\"roles\":[\"SERVICE\"],\"iat\":%d,\"exp\":%d%s}"
        .formatted(now.getEpochSecond(), now.plusSeconds(300).getEpochSecond(), act);
  }

  private static String sign(String secret, String payload) {
    String input =
        encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))
            + "."
            + encode(payload.getBytes(StandardCharsets.UTF_8));
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return input + "." + encode(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String encode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }
}
