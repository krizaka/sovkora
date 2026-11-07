package com.krizaka.sovkora.automationservice.infrastructure.config;

import com.krizaka.security.web.SecurityBaseline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security for the automation service.
 *
 * <p>The Krizaka baseline first (CORS preflight, health, info and the error page open; {@code
 * /internal/v1/**} for the {@code SERVICE} authority; everything else authenticated), then a valid
 * session JWT, verified locally against the shared HS256 secret. Per-resource access is the
 * caller's id in every statement, not a URL rule (ADR-035).
 *
 * <p><b>The one exception</b> (ADR-035 amendment, ADR-084): {@code POST /hooks/{triggerId}} is
 * open, because its authentication is the request's own signature, verified by the ingress before
 * any work. One verb, one path with one segment, never a wildcard — {@code SecurityExposureTest}
 * fails on anything else.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * The filter chain: the baseline, then the session JWT.
   *
   * @param http the builder
   * @param roles the {@code roles}-claim converter, with no authority prefix
   * @return the built chain
   * @throws Exception if the chain cannot be built
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationConverter roles) throws Exception {
    return SecurityBaseline.apply(
            http, auth -> auth.requestMatchers(HttpMethod.POST, "/hooks/{triggerId}").permitAll())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roles)))
        .build();
  }
}
