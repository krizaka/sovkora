package com.krizaka.sovkora.service.auth;

import static com.krizaka.sovkora.service.auth.TokenSigner.ISSUER;
import static com.krizaka.sovkora.service.auth.TokenSigner.SERVICE_KEY;
import static com.krizaka.sovkora.service.auth.TokenSigner.SESSION_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;

class TwoKeyJwtDecoderTest {

  private final TwoKeyJwtDecoder decoder =
      new TwoKeyJwtDecoder(SESSION_KEY, ISSUER, null, SERVICE_KEY);

  @Test
  void acceptsASessionFromTheIdentityIssuer() {
    assertThat(decoder.decode(TokenSigner.session(UnaryOperator.identity())).getSubject())
        .isEqualTo("user-1");
  }

  @Test
  void acceptsAServiceTokenSignedWithTheServiceKey() {
    assertThat(decoder.decode(TokenSigner.service(SERVICE_KEY, UnaryOperator.identity())))
        .satisfies(jwt -> assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SERVICE"));
  }

  @Test
  void refusesASessionFromAnotherIssuer() {
    String token = TokenSigner.session(c -> c.issuer("someone-else"));

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void refusesASessionWithoutAnIssuer() {
    String token = TokenSigner.session(c -> c.issuer(null));

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void refusesASessionAddressedToAnAudienceWhenNoneIsConfigured() {
    String token = TokenSigner.session(c -> c.audience("another-product"));

    assertThatThrownBy(() -> decoder.decode(token))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("names no audience");
  }

  @Test
  void requiresTheConfiguredAudienceOnASession() {
    TwoKeyJwtDecoder withAudience =
        new TwoKeyJwtDecoder(SESSION_KEY, ISSUER, "sovkora", SERVICE_KEY);

    assertThat(withAudience.decode(TokenSigner.session(c -> c.audience("sovkora")))).isNotNull();
    assertThatThrownBy(() -> withAudience.decode(TokenSigner.session(c -> c.audience("other"))))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("not addressed to sovkora");
    assertThatThrownBy(() -> withAudience.decode(TokenSigner.session(UnaryOperator.identity())))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void refusesAServiceTokenSignedWithTheSessionKey() {
    String forged = TokenSigner.service(SESSION_KEY, c -> c.issuer(ISSUER));

    assertThatThrownBy(() -> decoder.decode(forged))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("must be signed with the service-token key");
  }

  @Test
  void refusesASessionSignedWithTheServiceKey() {
    String session = TokenSigner.service(SERVICE_KEY, c -> c.claim("roles", List.of("ROLE_ADMIN")));

    assertThatThrownBy(() -> decoder.decode(session))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("signs SERVICE tokens only");
  }

  @Test
  void refusesAServiceTokenThatAddsARoleToService() {
    String token =
        TokenSigner.service(SERVICE_KEY, c -> c.claim("roles", List.of("SERVICE", "ROLE_ADMIN")));

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void refusesAServiceTokenThatNamesAnIssuerOrAnAudience() {
    String withIssuer = TokenSigner.service(SERVICE_KEY, c -> c.issuer("anyone"));
    String withAudience = TokenSigner.service(SERVICE_KEY, c -> c.audience("anyone"));

    assertThatThrownBy(() -> decoder.decode(withIssuer))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("names no issuer");
    assertThatThrownBy(() -> decoder.decode(withAudience))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("names no audience");
  }

  @Test
  void refusesATokenSignedWithAnyOtherKey() {
    String token =
        TokenSigner.service("an-unknown-key-0123456789abcdef01234567", UnaryOperator.identity());

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void refusesAnUnsignedToken() {
    String payload = "{\"sub\":\"x\",\"roles\":[\"SERVICE\"],\"exp\":9999999999}";
    String token = encode("{\"alg\":\"none\"}") + "." + encode(payload) + ".";

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
  }

  @Test
  void refusesAnExpiredServiceToken() {
    String token =
        TokenSigner.service(
            SERVICE_KEY, c -> c.expirationTime(Date.from(Instant.now().minusSeconds(600))));

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void refusesTheSameKeyForBothKinds() {
    assertThatThrownBy(() -> new TwoKeyJwtDecoder(SESSION_KEY, ISSUER, null, SESSION_KEY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must differ from IDENTITY_JWT_SECRET");
  }

  private static String encode(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }
}
