package com.krizaka.sovkora.job.infrastructure.config;

import com.krizaka.security.web.SecurityBaseline;
import com.krizaka.sovkora.job.application.service.PodTokenService;
import com.krizaka.sovkora.job.domain.model.PodTokenPolicy;
import com.krizaka.sovkora.job.infrastructure.support.PodTokenDecoder;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security for the job service.
 *
 * <p>The service was a pure AMQP executor with no HTTP business surface. It used to express that as
 * {@code anyRequest().permitAll()}, which is a different statement: "there is nothing to protect"
 * is a fact about this afternoon, while the filter chain outlives it. That afternoon has now
 * arrived — {@code /internal/v1/capabilities} serves the routing table (ADR-037) — and it landed
 * behind {@code hasAuthority("SERVICE")} because the chain had already been written to expect it.
 *
 * <p>The chain is the Krizaka security baseline every service shares: actuator health/info and
 * preflight open, everything else authenticated against the shared HS256 session secret, with the
 * {@code roles} claim mapped straight onto authorities.
 *
 * <p>A RunPod pod is the one caller that is not a service (krizaka-internal#3645): it presents its
 * own token, minted here, and the baseline reserves {@code /internal/v1/**} for {@code SERVICE}
 * before any rule of this service runs. So a request bearing a pod token goes through a chain of
 * its own, ordered first: the four routes the media worker calls, and nothing else.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /** The pod role, named here so the generated API reference reads it (krizaka-internal#3645). */
  private static final String GPU_POD = PodTokenPolicy.ROLE;

  /**
   * The pods' chain: only requests whose bearer token names the pod key reach it, and it accepts a
   * live pod token on the media worker's four routes ({@code docs/WORKER_PROTOCOL.md}) only —
   * registration, heartbeat, a job's output upload and a job's input image. Every other route,
   * {@code /internal/v1/**} and {@code /api/**} alike, is denied. A forged header gains nothing:
   * the token is then verified against the pod key, its claims and its revocation row.
   *
   * @param http the builder
   * @param roles the {@code roles}-claim converter, with no authority prefix
   * @param properties the pod token key
   * @param tokens the revocation rows
   * @param clock the source of "now" for {@code exp}
   * @return the built chain
   * @throws Exception if the chain cannot be built
   */
  @Bean
  @Order(1)
  public SecurityFilterChain podTokenFilterChain(
      HttpSecurity http,
      JwtAuthenticationConverter roles,
      PodTokenProperties properties,
      PodTokenService tokens,
      Clock clock)
      throws Exception {
    PodTokenDecoder pods = new PodTokenDecoder(properties.signingKey(), tokens, clock);
    return http.securityMatcher(PodTokenDecoder::carriesPodToken)
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/internal/v1/workers")
                    .hasAuthority(GPU_POD)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/workers/*/heartbeat")
                    .hasAuthority(GPU_POD)
                    .requestMatchers(HttpMethod.PUT, "/internal/v1/jobs/*/output/*")
                    .hasAuthority(GPU_POD)
                    .requestMatchers(HttpMethod.GET, "/internal/v1/jobs/*/input/image")
                    .hasAuthority(GPU_POD)
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.decoder(pods).jwtAuthenticationConverter(roles)))
        .build();
  }

  /**
   * The filter chain: the Krizaka security baseline, then a valid session JWT for everything else.
   *
   * <p>The baseline ({@link SecurityBaseline}) opens the CORS preflight, health, info and the error
   * page, and reserves {@code /internal/v1/**} for the {@code SERVICE} authority — authenticated,
   * not merely unrouted: the edge not routing {@code /internal/**} is topology, and one SSRF turns
   * topology into an anonymous call (ADR-035). The session decoder and the {@code roles}-claim
   * converter come from krizaka-security ({@code krizaka.security.jwt.secret}).
   *
   * @param http the builder
   * @param roles the {@code roles}-claim converter, with no authority prefix
   * @return the built chain
   * @throws Exception if the chain cannot be built
   */
  @Bean
  @Order(2)
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationConverter roles) throws Exception {
    return SecurityBaseline.apply(http, auth -> {})
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roles)))
        .build();
  }
}
