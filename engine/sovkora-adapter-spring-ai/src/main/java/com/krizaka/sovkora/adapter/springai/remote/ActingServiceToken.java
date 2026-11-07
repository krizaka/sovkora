package com.krizaka.sovkora.adapter.springai.remote;

import com.krizaka.sovkora.core.domain.model.security.Actor;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The service token of a call made on an actor's behalf (ADR-091): the machine-to-machine token of
 * {@code ServiceTokenProvider} ({@code roles: [SERVICE]}, HS256, five minutes) with the verified
 * principal in an {@code act} claim. The claim is covered by the signature, so the service that
 * receives it trusts it exactly as far as it trusts the shared secret; a body or a header the user
 * controls never names the actor.
 *
 * <p>The names in an {@link Actor} are validated against a pattern that cannot hold a quote, a
 * backslash or a comma, so writing them into the JSON needs no escaping.
 */
final class ActingServiceToken {

  private static final Duration LIFETIME = Duration.ofMinutes(5);
  private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
  private static final int MINIMUM_SECRET_LENGTH = 32;

  private final byte[] secret;
  private final String subject;
  private final Clock clock;

  ActingServiceToken(String secret, String subject) {
    this(secret, subject, Clock.systemUTC());
  }

  ActingServiceToken(String secret, String subject, Clock clock) {
    if (secret == null || secret.length() < MINIMUM_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          "the HS256 secret must be at least " + MINIMUM_SECRET_LENGTH + " characters");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.subject = subject;
    this.clock = clock;
  }

  /** A freshly signed service token acting for {@code actor}. */
  String tokenFor(Actor actor) {
    Instant now = clock.instant();
    String payload =
        "{\"sub\":\"%s\",\"roles\":[\"SERVICE\"],\"iat\":%d,\"exp\":%d,\"act\":{\"sub\":\"%s\",\"tenant\":\"%s\",\"roles\":%s,\"groups\":%s}}"
            .formatted(
                subject,
                now.getEpochSecond(),
                now.plus(LIFETIME).getEpochSecond(),
                actor.subject(),
                actor.tenant(),
                list(actor.roles()),
                list(actor.groups()));
    String signingInput =
        encode(HEADER.getBytes(StandardCharsets.UTF_8))
            + "."
            + encode(payload.getBytes(StandardCharsets.UTF_8));
    return signingInput + "." + encode(sign(signingInput));
  }

  private static String list(Set<String> names) {
    return new TreeSet<>(names)
        .stream().map(n -> "\"" + n + "\"").collect(Collectors.joining(",", "[", "]"));
  }

  private byte[] sign(String signingInput) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("cannot sign the service token", e);
    }
  }

  private static String encode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }
}
