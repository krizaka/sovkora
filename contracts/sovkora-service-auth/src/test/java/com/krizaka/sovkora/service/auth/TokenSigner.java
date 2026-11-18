package com.krizaka.sovkora.service.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.UnaryOperator;

/** Signs the tokens the tests present: sessions, service tokens and their forgeries. */
final class TokenSigner {

  static final String SESSION_KEY = "test-session-key-0123456789abcdef0123";
  static final String SERVICE_KEY = "test-service-key-fedcba9876543210fedc";
  static final String ISSUER = "krizaka-users";

  private TokenSigner() {}

  /** A session the identity service would issue: its issuer, a user role, signed with its key. */
  static String session(UnaryOperator<JWTClaimsSet.Builder> edit) {
    return sign(
        SESSION_KEY,
        edit.apply(base("user-1").issuer(ISSUER).claim("roles", List.of("ROLE_USER"))));
  }

  /** A service token as krizaka-security's provider mints it: no iss, no aud, SERVICE only. */
  static String service(String key, UnaryOperator<JWTClaimsSet.Builder> edit) {
    return sign(key, edit.apply(base("sovkora-studio-service").claim("roles", List.of("SERVICE"))));
  }

  static String sign(String key, JWTClaimsSet.Builder claims) {
    try {
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
      jwt.sign(new MACSigner(key.getBytes(StandardCharsets.UTF_8)));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private static JWTClaimsSet.Builder base(String subject) {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .subject(subject)
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plusSeconds(300)));
  }
}
