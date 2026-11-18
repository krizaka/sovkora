package com.krizaka.sovkora.service.auth;

import com.krizaka.security.jwt.SessionJwtAutoConfiguration;
import com.krizaka.security.jwt.SessionJwtProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The token verification of every Sovkora service that verifies tokens: one decoder, two keys
 * ({@link TwoKeyJwtDecoder}).
 *
 * <p>It runs before krizaka-security's {@link SessionJwtAutoConfiguration} and contributes the bean
 * that one would have, {@code sessionJwtDecoder}, so the kit's single-key decoder backs off. Unlike
 * the kit's, it is not conditional on a property being present: a service that has this module on
 * its classpath does not start without {@code IDENTITY_JWT_SECRET}, {@code IDENTITY_JWT_ISSUER}'s
 * value and {@code AUTH_SERVICE_TOKEN_SECRET} — a missing variable is a refused start, never a
 * fallback to a value written in a file.
 */
@AutoConfiguration(before = SessionJwtAutoConfiguration.class)
@ConditionalOnClass(JwtDecoder.class)
@EnableConfigurationProperties({SessionJwtProperties.class, ServiceTokenProperties.class})
public class ServiceAuthAutoConfiguration {

  /**
   * The decoder every resource-server chain of the service uses.
   *
   * @param session the identity session key, issuer and audience ({@code krizaka.security.jwt})
   * @param service the service-token key ({@code sovkora.security.service-token})
   * @return a decoder that tells a session from a service token by the key that signed it
   * @throws IllegalStateException when the session issuer is missing, or the session is configured
   *     for issuer keys (JWKS), which this decoder does not verify yet
   */
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  public JwtDecoder sessionJwtDecoder(
      SessionJwtProperties session, ServiceTokenProperties service) {
    // A missing key is refused by SessionJwtProperties itself, when it binds; no key here means
    // the issuer-keys mode, which the two-key split does not cover yet.
    if (session.secret() == null) {
      throw new IllegalStateException(
          "krizaka.security.jwt.jwk-set-uri is not supported by the Sovkora services yet: set"
              + " IDENTITY_JWT_SECRET (krizaka.security.jwt.secret)");
    }
    if (session.issuer() == null) {
      throw new IllegalStateException(
          "krizaka.security.jwt.issuer is not set (IDENTITY_JWT_ISSUER): a session must name the"
              + " identity issuer");
    }
    return new TwoKeyJwtDecoder(
        session.secret(), session.issuer(), session.audience(), service.secret());
  }
}
