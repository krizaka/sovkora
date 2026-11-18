package com.krizaka.sovkora.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.security.jwt.SessionJwtAutoConfiguration;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * A service context: started with both keys and the issuer it verifies; refused, with a message
 * that names what is missing, without any of them.
 */
class ServiceAuthAutoConfigurationTest {

  private static final String SESSION = "krizaka.security.jwt.secret=" + TokenSigner.SESSION_KEY;
  private static final String ISSUER = "krizaka.security.jwt.issuer=" + TokenSigner.ISSUER;
  private static final String SERVICE =
      "sovkora.security.service-token.secret=" + TokenSigner.SERVICE_KEY;

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ServiceAuthAutoConfiguration.class, SessionJwtAutoConfiguration.class));

  @Test
  void startsWithBothKeysAndTheIssuerAndReplacesTheKitDecoder() {
    runner
        .withPropertyValues(SESSION, ISSUER, SERVICE)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(JwtDecoder.class);
              assertThat(context.getBean("sessionJwtDecoder")).isInstanceOf(TwoKeyJwtDecoder.class);
              JwtDecoder decoder = context.getBean(JwtDecoder.class);
              assertThat(decoder.decode(TokenSigner.session(UnaryOperator.identity()))).isNotNull();
            });
  }

  @Test
  void refusesToStartWithoutTheSessionKey() {
    runner
        .withPropertyValues(ISSUER, SERVICE)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("krizaka.security.jwt.secret"));
  }

  @Test
  void refusesToStartWithAnEmptySessionKey() {
    runner
        .withPropertyValues("krizaka.security.jwt.secret=", ISSUER, SERVICE)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("krizaka.security.jwt.secret"));
  }

  @Test
  void refusesToStartWithoutTheServiceTokenKey() {
    runner
        .withPropertyValues(SESSION, ISSUER)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("AUTH_SERVICE_TOKEN_SECRET is not set"));
  }

  @Test
  void refusesToStartWithoutTheSessionIssuer() {
    runner
        .withPropertyValues(SESSION, SERVICE)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("krizaka.security.jwt.issuer is not set"));
  }

  @Test
  void refusesToStartWhenBothKindsShareOneKey() {
    runner
        .withPropertyValues(
            SESSION, ISSUER, "sovkora.security.service-token.secret=" + TokenSigner.SESSION_KEY)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("must differ from IDENTITY_JWT_SECRET"));
  }
}
