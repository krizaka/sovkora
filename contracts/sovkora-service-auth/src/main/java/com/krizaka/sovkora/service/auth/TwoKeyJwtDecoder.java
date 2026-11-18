package com.krizaka.sovkora.service.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies a bearer token against the one key its kind is signed with, and checks the claims that
 * kind must carry.
 *
 * <ul>
 *   <li>A <b>service token</b> is signed with the service-token key and carries exactly the {@code
 *       SERVICE} role. The platform's minter ({@code ServiceTokenProvider}, krizaka-security) sets
 *       neither {@code iss} nor {@code aud}, so a service token that names either was not minted by
 *       a Sovkora service and is refused.
 *   <li>A <b>session</b> is signed with the identity session key, names the identity issuer in
 *       {@code iss}, names the configured audience in {@code aud} (or carries none when no audience
 *       is configured), and never carries {@code SERVICE}: a {@code SERVICE} token signed with the
 *       session key is exactly the forgery this split exists to stop.
 * </ul>
 *
 * <p>Both kinds are HS256 only; {@code alg: none} and every other algorithm are refused. The key
 * that verifies a token decides its kind, never a claim inside it. Thread-safe.
 */
final class TwoKeyJwtDecoder implements JwtDecoder {

  /** The claim that carries a token's authorities. */
  static final String ROLES = "roles";

  /** The one authority a service token carries. */
  static final String SERVICE = "SERVICE";

  private final MACVerifier serviceKey;
  private final JwtDecoder services;
  private final JwtDecoder sessions;

  /**
   * @param sessionSecret the identity session key
   * @param sessionIssuer the only {@code iss} a session may carry
   * @param sessionAudience the {@code aud} a session must name, or {@code null} for none
   * @param serviceSecret the service-token key, never the session key
   */
  TwoKeyJwtDecoder(
      String sessionSecret, String sessionIssuer, String sessionAudience, String serviceSecret) {
    if (sessionSecret.equals(serviceSecret)) {
      throw new IllegalArgumentException(
          "AUTH_SERVICE_TOKEN_SECRET must differ from IDENTITY_JWT_SECRET: a service token signed"
              + " with the session key could not be told from a forged one");
    }
    try {
      this.serviceKey = new MACVerifier(serviceSecret.getBytes(StandardCharsets.UTF_8));
    } catch (JOSEException e) {
      throw new IllegalArgumentException("AUTH_SERVICE_TOKEN_SECRET is not a usable HS256 key", e);
    }
    this.services =
        decoder(
            serviceSecret,
            JwtValidators.createDefault(),
            absent("iss", "a service token names no issuer"),
            absent("aud", "a service token names no audience"),
            serviceRoleOnly());
    this.sessions =
        decoder(
            sessionSecret,
            JwtValidators.createDefaultWithIssuer(sessionIssuer),
            sessionAudience == null
                ? absent("aud", "a session names no audience")
                : audience(sessionAudience),
            noServiceRole());
  }

  @Override
  public Jwt decode(String token) {
    return signedWithServiceKey(token) ? services.decode(token) : sessions.decode(token);
  }

  private boolean signedWithServiceKey(String token) {
    SignedJWT jws;
    try {
      jws = SignedJWT.parse(token);
    } catch (ParseException e) {
      throw new BadJwtException("Malformed token", e);
    }
    if (!JWSAlgorithm.HS256.equals(jws.getHeader().getAlgorithm())) {
      throw new BadJwtException("Unsupported token algorithm");
    }
    try {
      return jws.verify(serviceKey);
    } catch (JOSEException e) {
      throw new BadJwtException("Unverifiable token", e);
    }
  }

  @SafeVarargs
  private static JwtDecoder decoder(String secret, OAuth2TokenValidator<Jwt>... validators) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(validators)));
    return decoder;
  }

  private static OAuth2TokenValidator<Jwt> absent(String claim, String reason) {
    return jwt -> jwt.hasClaim(claim) ? failure(reason) : OAuth2TokenValidatorResult.success();
  }

  private static OAuth2TokenValidator<Jwt> audience(String expected) {
    return jwt -> {
      List<String> audience = jwt.getAudience();
      return audience != null && audience.contains(expected)
          ? OAuth2TokenValidatorResult.success()
          : failure("the session is not addressed to " + expected);
    };
  }

  private static OAuth2TokenValidator<Jwt> serviceRoleOnly() {
    return jwt ->
        List.of(SERVICE).equals(jwt.getClaimAsStringList(ROLES))
            ? OAuth2TokenValidatorResult.success()
            : failure("the service-token key signs SERVICE tokens only");
  }

  private static OAuth2TokenValidator<Jwt> noServiceRole() {
    return jwt -> {
      List<String> roles = jwt.getClaimAsStringList(ROLES);
      return roles != null && roles.contains(SERVICE)
          ? failure("a SERVICE token must be signed with the service-token key")
          : OAuth2TokenValidatorResult.success();
    };
  }

  private static OAuth2TokenValidatorResult failure(String reason) {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", reason, null));
  }
}
