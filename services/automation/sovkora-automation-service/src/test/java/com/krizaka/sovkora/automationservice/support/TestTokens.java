package com.krizaka.sovkora.automationservice.support;

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

/** Signs session tokens the way the identity service does: HS256, a {@code roles} claim. */
public final class TestTokens {

  private TestTokens() {}

  /**
   * A signed-in user's session token.
   *
   * @param subject the actor id
   * @param roles the {@code roles} claim
   * @return the compact JWS
   */
  public static String session(String subject, String... roles) {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .claim("roles", List.of(roles))
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build();
    try {
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jwt.sign(new MACSigner(AutomationServiceIT.JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }
}
