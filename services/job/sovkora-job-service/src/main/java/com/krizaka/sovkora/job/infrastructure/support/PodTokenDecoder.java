package com.krizaka.sovkora.job.infrastructure.support;

import com.krizaka.sovkora.job.application.service.PodTokenService;
import com.krizaka.sovkora.job.domain.model.PodKey;
import com.krizaka.sovkora.job.domain.model.PodTokenPolicy;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import jakarta.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies a GPU pod's job service token (krizaka-internal#3645): the pod key's HS256 signature,
 * {@code exp} with no skew, {@code aud} = the job service, {@code roles} = exactly the pod role,
 * {@code sub} = {@code gpu-pod:<key>}, and a live revocation row for its {@code jti}.
 *
 * <p>Deliberately not a bean: a {@code JwtDecoder} bean would make krizaka-security's session
 * decoder back off for the whole service.
 */
public final class PodTokenDecoder implements JwtDecoder {

  private static final Logger logger = LoggerFactory.getLogger(PodTokenDecoder.class);

  private static final String BEARER = "Bearer ";
  private static final OAuth2Error REFUSED =
      new OAuth2Error("invalid_token", "not a live pod token", null);

  private final JwtDecoder nimbus;

  /**
   * @param key the pod token key
   * @param tokens where a token's revocation row is read
   * @param clock the source of "now" for {@code exp}
   */
  public PodTokenDecoder(SecretKey key, PodTokenService tokens, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    JwtTimestampValidator expiry = new JwtTimestampValidator(Duration.ZERO);
    expiry.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(expiry, PodTokenDecoder::podClaims, live(tokens)));
    this.nimbus = decoder;
  }

  @Override
  public Jwt decode(String token) throws JwtException {
    return nimbus.decode(token);
  }

  /**
   * Whether a request presents a pod token: its bearer's JOSE header names the pod key. This only
   * routes the request to the pods' filter chain, which then verifies the token; a forged header
   * gains nothing there.
   *
   * @param request the request
   * @return {@code true} when the bearer token's {@code kid} is the pod key's
   */
  public static boolean carriesPodToken(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith(BEARER)) {
      return false;
    }
    String token = authorization.substring(BEARER.length()).trim();
    int dot = token.indexOf('.');
    if (dot <= 0) {
      return false;
    }
    try {
      JWSHeader header = JWSHeader.parse(new Base64URL(token.substring(0, dot)));
      return PodTokenPolicy.KEY_ID.equals(header.getKeyID());
    } catch (ParseException | IllegalArgumentException e) {
      // Not a JOSE header: the session chain reads the bearer, and refuses it there.
      logger.debug("a bearer token without a JOSE header is not a pod token", e);
      return false;
    }
  }

  private static OAuth2TokenValidatorResult podClaims(Jwt jwt) {
    List<String> audience = jwt.getAudience();
    List<String> roles = jwt.getClaimAsStringList("roles");
    String subject = jwt.getSubject();
    boolean pod =
        audience != null
            && audience.equals(List.of(PodTokenPolicy.AUDIENCE))
            && roles != null
            && roles.equals(List.of(PodTokenPolicy.ROLE))
            && subject != null
            && subject.startsWith(PodKey.SUBJECT_PREFIX)
            && jwt.getId() != null;
    return pod ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(REFUSED);
  }

  private static OAuth2TokenValidator<Jwt> live(PodTokenService tokens) {
    return jwt -> {
      try {
        boolean live =
            tokens.isLive(UUID.fromString(jwt.getId()), PodKey.ofSubject(jwt.getSubject()));
        return live
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(REFUSED);
      } catch (IllegalArgumentException e) {
        logger.debug("a pod token with a malformed jti or subject", e);
        return OAuth2TokenValidatorResult.failure(REFUSED);
      }
    };
  }
}
