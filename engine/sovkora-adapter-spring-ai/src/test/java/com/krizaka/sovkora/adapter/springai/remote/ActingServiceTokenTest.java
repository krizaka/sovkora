package com.krizaka.sovkora.adapter.springai.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.domain.model.security.Actor;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActingServiceTokenTest {

  private static final String SECRET = "sovkora-local-dev-identity-jwt-secret-256bit!";

  private final ActingServiceToken tokens =
      new ActingServiceToken(
          SECRET,
          "sovkora-core",
          Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC));

  @Test
  @DisplayName(
      "the token is a SERVICE token whose act claim names the actor, signed with the secret")
  void signedAndActing() throws Exception {
    Actor actor =
        Actor.of("emp-1", List.of("ROLE_USER", "GROUP_FINANCE", "TENANT_acme")).orElseThrow();

    String[] parts = tokens.tokenFor(actor).split("\\.");
    String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

    assertThat(payload)
        .contains("\"roles\":[\"SERVICE\"]")
        .contains(
            "\"act\":{\"sub\":\"emp-1\",\"tenant\":\"acme\",\"roles\":[\"ROLE_USER\"],\"groups\":[\"GROUP_FINANCE\"]}")
        .contains("\"exp\":1793016300");
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
    assertThat(Base64.getUrlDecoder().decode(parts[2])).isEqualTo(expected);
  }

  @Test
  @DisplayName("a short secret is refused")
  void shortSecretRefused() {
    assertThatThrownBy(() -> new ActingServiceToken("short", "sovkora-core"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
