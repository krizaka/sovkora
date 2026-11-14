package com.krizaka.sovkora.job.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.security.jwt.SessionJwtAutoConfiguration;
import com.krizaka.security.jwt.SessionJwtProperties;
import com.krizaka.security.token.ServiceTokenProvider;
import com.krizaka.sovkora.job.application.service.PodTokenService;
import com.krizaka.sovkora.job.domain.model.PodKey;
import com.krizaka.sovkora.job.domain.model.PodTokenPolicy;
import com.krizaka.sovkora.job.infrastructure.config.PodTokenTestWiring.MutableClock;
import com.krizaka.sovkora.test.support.AbstractContainerIntegrationTest;
import jakarta.servlet.Filter;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * The job service's real filter chains against a GPU pod's token (krizaka-internal#3645), with its
 * revocation rows on a real PostgreSQL: accepted on the media worker's four routes and nowhere
 * else, refused once revoked or past {@code exp}, and refused by every other service's decoder.
 */
@SpringJUnitWebConfig(SecurityConfigIT.Context.class)
class SecurityConfigIT extends AbstractContainerIntegrationTest {

  private static final PodKey POD = new PodKey("20261114180000-a1b2c3");
  private static final String JOB = UUID.randomUUID().toString();

  /**
   * The four routes the media worker calls, exactly (fresh builders: a builder keeps its headers).
   */
  private static List<MockHttpServletRequestBuilder> podRoutes() {
    return List.of(
        post("/internal/v1/workers"),
        post("/internal/v1/workers/sovkora-worker-media/heartbeat"),
        put("/internal/v1/jobs/" + JOB + "/output/video.mp4"),
        get("/internal/v1/jobs/" + JOB + "/input/image"));
  }

  /** Every other kind of route this service has, internal and public. */
  private static List<MockHttpServletRequestBuilder> otherRoutes() {
    return List.of(
        get("/internal/v1/capabilities/media.vision"),
        post("/internal/v1/gpu-pods/" + POD.value() + "/token"),
        delete("/internal/v1/gpu-pods/" + POD.value() + "/token"),
        post("/internal/v1/gpu-pods/tokens/sweep"),
        get("/internal/v1/workers"),
        delete("/internal/v1/jobs/" + JOB + "/output/video.mp4"),
        get("/internal/v1/jobs/" + JOB + "/output/video.mp4"),
        post("/internal/v1/jobs/" + JOB + "/erasure"),
        get("/api/v1/jobs/" + JOB),
        get("/api/v1/models"));
  }

  @Autowired WebApplicationContext context;
  @Autowired PodTokenService tokens;
  @Autowired JdbcTemplate jdbc;
  @Autowired MutableClock clock;
  @Autowired PodTokenProperties properties;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    jdbc.update("DELETE FROM gpu_pod_tokens");
    clock.set(PodTokenTestWiring.T0);
    mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
            .build();
  }

  private String podToken() {
    return tokens.mint(POD, Duration.ofHours(2)).token();
  }

  private static RequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
    return request.header("Authorization", "Bearer " + token);
  }

  private static String serviceToken() {
    return new ServiceTokenProvider(
            PodTokenTestWiring.IDENTITY_SECRET, "sovkora-studio-service", Clock.systemUTC())
        .token();
  }

  @Test
  @DisplayName("a live pod token reaches the media worker's four routes")
  void podTokenOnItsRoutes() throws Exception {
    String token = podToken();
    for (MockHttpServletRequestBuilder route : podRoutes()) {
      mvc.perform(bearer(route, token)).andExpect(status().isOk());
    }
  }

  @Test
  @DisplayName("every other route refuses it, /internal/v1/** and /api/** alike")
  void podTokenNowhereElse() throws Exception {
    String token = podToken();
    for (MockHttpServletRequestBuilder route : otherRoutes()) {
      mvc.perform(bearer(route, token)).andExpect(status().isForbidden());
    }
  }

  @Test
  @DisplayName("once revoked (runpod stop, or the sweep) it is refused")
  void revokedIsRefused() throws Exception {
    String token = podToken();
    tokens.revoke(POD);
    for (MockHttpServletRequestBuilder route : podRoutes()) {
      mvc.perform(bearer(route, token)).andExpect(status().isUnauthorized());
    }
  }

  @Test
  @DisplayName("swept with the other ended pods, it is refused")
  void sweptIsRefused() throws Exception {
    String token = podToken();
    tokens.sweep(List.of(new PodKey("20261114170000-d4e5f6")));
    mvc.perform(bearer(podRoutes().getFirst(), token)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("past its exp it is refused, with no clock skew")
  void expiredIsRefused() throws Exception {
    String token = podToken();
    clock.set(PodTokenTestWiring.T0.plus(Duration.ofHours(2)));
    mvc.perform(bearer(podRoutes().getFirst(), token)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("a token signed with the pod key but never recorded here is refused")
  void unrecordedIsRefused() throws Exception {
    String token = podToken();
    jdbc.update("DELETE FROM gpu_pod_tokens");
    mvc.perform(bearer(podRoutes().getFirst(), token)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("the pod key's signature with SERVICE in roles is refused: the pod role only")
  void podKeyCannotSignService() throws Exception {
    String token = sign(properties, "SERVICE", PodTokenPolicy.KEY_ID);
    mvc.perform(bearer(podRoutes().getFirst(), token)).andExpect(status().isUnauthorized());
    mvc.perform(bearer(otherRoutes().getFirst(), token)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "a SERVICE token (identity secret) with the pod kid gains nothing: the pod key verifies it")
  void forgedKidIsRefused() throws Exception {
    JwtEncoder identity =
        NimbusJwtEncoder.withSecretKey(
                new javax.crypto.spec.SecretKeySpec(
                    PodTokenTestWiring.IDENTITY_SECRET.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8),
                    "HmacSHA256"))
            .algorithm(MacAlgorithm.HS256)
            .jwkPostProcessor(jwk -> jwk.keyID(PodTokenPolicy.KEY_ID))
            .build();
    String token = encode(identity, "SERVICE", PodTokenPolicy.KEY_ID);
    mvc.perform(bearer(podRoutes().getFirst(), token)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("a pod token without its kid falls to the session chain, which refuses its key")
  void podTokenWithoutKidIsRefused() throws Exception {
    tokens.mint(POD, Duration.ofHours(2));
    String token = sign(properties, PodTokenPolicy.ROLE, null);
    mvc.perform(bearer(podRoutes().getFirst(), token)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("the other services refuse it: their session decoder does not hold the pod key")
  void otherServicesRefuseIt() {
    String token = podToken();
    JwtDecoder otherService =
        new SessionJwtAutoConfiguration()
            .sessionJwtDecoder(
                new SessionJwtProperties(
                    PodTokenTestWiring.IDENTITY_SECRET, null, null, null, null, null));
    assertThatThrownBy(() -> otherService.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("a SERVICE token still reaches /internal/v1, the pod routes and the mint route")
  void serviceTokenUnchanged() throws Exception {
    String token = serviceToken();
    for (MockHttpServletRequestBuilder route : podRoutes()) {
      mvc.perform(bearer(route, token)).andExpect(status().isOk());
    }
    mvc.perform(bearer(otherRoutes().getFirst(), token)).andExpect(status().isOk());
    mvc.perform(bearer(otherRoutes().get(1), token)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("with no token, the pod routes are refused")
  void anonymousIsRefused() throws Exception {
    mvc.perform(podRoutes().getFirst()).andExpect(status().isUnauthorized());
  }

  /** A token signed with the pod key, recorded for {@link #POD}, with the given role and kid. */
  private String sign(PodTokenProperties keys, String role, String kid) {
    var builder = NimbusJwtEncoder.withSecretKey(keys.signingKey()).algorithm(MacAlgorithm.HS256);
    if (kid != null) {
      builder.jwkPostProcessor(jwk -> jwk.keyID(kid));
    }
    return encode(builder.build(), role, kid);
  }

  private String encode(JwtEncoder encoder, String role, String kid) {
    UUID jti = UUID.randomUUID();
    jdbc.update("DELETE FROM gpu_pod_tokens");
    jdbc.update(
        "INSERT INTO gpu_pod_tokens (jti, pod_key, issued_at, expires_at) VALUES (?, ?, ?, ?)",
        jti,
        POD.value(),
        java.sql.Timestamp.from(PodTokenTestWiring.T0),
        java.sql.Timestamp.from(PodTokenTestWiring.T0.plus(Duration.ofHours(2))));
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(POD.subject())
            .audience(List.of(PodTokenPolicy.AUDIENCE))
            .claim("roles", List.of(role))
            .id(jti.toString())
            .issuedAt(PodTokenTestWiring.T0)
            .expiresAt(PodTokenTestWiring.T0.plus(Duration.ofHours(2)))
            .build();
    JwsHeader.Builder header = JwsHeader.with(MacAlgorithm.HS256);
    if (kid != null) {
      header.keyId(kid);
    }
    return encoder.encode(JwtEncoderParameters.from(header.build(), claims)).getTokenValue();
  }

  @Configuration
  @EnableWebMvc
  @Import({SecurityConfig.class, PodTokenTestWiring.class})
  static class Context {

    @Bean
    JwtDecoder sessionJwtDecoder() {
      return new SessionJwtAutoConfiguration()
          .sessionJwtDecoder(
              new SessionJwtProperties(
                  PodTokenTestWiring.IDENTITY_SECRET, null, null, null, null, null));
    }

    @Bean
    JwtAuthenticationConverter roles() {
      return new SessionJwtAutoConfiguration().sessionJwtAuthenticationConverter();
    }

    @Bean
    Probe probe() {
      return new Probe();
    }
  }

  /** Answers 200 on every path: what is under test is who the chains let through. */
  @RestController
  static class Probe {
    @RequestMapping("/**")
    String any() {
      return "ok";
    }
  }
}
