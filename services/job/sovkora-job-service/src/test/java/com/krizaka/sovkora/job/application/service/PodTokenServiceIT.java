package com.krizaka.sovkora.job.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.job.domain.model.IssuedPodToken;
import com.krizaka.sovkora.job.domain.model.PodKey;
import com.krizaka.sovkora.job.domain.model.PodTokenPolicy;
import com.krizaka.sovkora.job.infrastructure.config.PodTokenTestWiring;
import com.krizaka.sovkora.job.infrastructure.config.PodTokenTestWiring.MutableClock;
import com.krizaka.sovkora.test.support.AbstractContainerIntegrationTest;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * A GPU pod's job service token (krizaka-internal#3645), minted, checked and revoked against the
 * real {@code gpu_pod_tokens} table of {@code V4} on a real PostgreSQL.
 */
@SpringJUnitConfig(PodTokenTestWiring.class)
class PodTokenServiceIT extends AbstractContainerIntegrationTest {

  private static final PodKey POD = new PodKey("20261114180000-a1b2c3");
  private static final PodKey OTHER = new PodKey("20261114170000-d4e5f6");

  @Autowired PodTokenService tokens;
  @Autowired JdbcTemplate jdbc;
  @Autowired MutableClock clock;

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM gpu_pod_tokens");
    clock.set(PodTokenTestWiring.T0);
  }

  @Test
  @DisplayName("the token is addressed to the job service, carries the pod role and names its pod")
  void claims() throws Exception {
    IssuedPodToken issued = tokens.mint(POD, Duration.ofHours(8).plusMinutes(30));

    SignedJWT jwt = SignedJWT.parse(issued.token());
    var claims = jwt.getJWTClaimsSet();
    assertThat(claims.getAudience()).containsExactly(PodTokenPolicy.AUDIENCE);
    assertThat(claims.getStringListClaim("roles")).containsExactly("GPU_POD");
    assertThat(claims.getStringListClaim("roles")).doesNotContain("SERVICE");
    assertThat(claims.getSubject()).isEqualTo("gpu-pod:20261114180000-a1b2c3");
    assertThat(claims.getJWTID()).isEqualTo(issued.jti().toString());
    assertThat(claims.getExpirationTime().toInstant())
        .isEqualTo(PodTokenTestWiring.T0.plus(Duration.ofHours(8).plusMinutes(30)))
        .isEqualTo(issued.expiresAt());
    assertThat(jwt.getHeader().getKeyID()).isEqualTo(PodTokenPolicy.KEY_ID);
    assertThat(issued.toString()).doesNotContain(issued.token());
  }

  @Test
  @DisplayName("its expiry is the pod's lifetime cap, never beyond the service's maximum")
  void lifetimeIsCapped() {
    IssuedPodToken issued = tokens.mint(POD, Duration.ofDays(3));

    assertThat(issued.expiresAt())
        .isEqualTo(PodTokenTestWiring.T0.plus(Duration.parse("PT12H30M")));
    assertThatThrownBy(() -> tokens.mint(POD, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("live until revoked; past its expiry it is not live either")
  void revocationAndExpiry() {
    IssuedPodToken issued = tokens.mint(POD, Duration.ofHours(1));
    assertThat(tokens.isLive(issued.jti(), POD)).isTrue();
    assertThat(tokens.isLive(issued.jti(), OTHER)).as("the row names its pod").isFalse();

    clock.set(PodTokenTestWiring.T0.plus(Duration.ofHours(1)));
    assertThat(tokens.isLive(issued.jti(), POD)).as("at exp").isFalse();

    clock.set(PodTokenTestWiring.T0);
    assertThat(tokens.revoke(POD)).isEqualTo(1);
    assertThat(tokens.isLive(issued.jti(), POD)).isFalse();
    assertThat(tokens.revoke(POD)).as("a second revoke finds nothing").isZero();
  }

  @Test
  @DisplayName("a pod holds one live token: a second mint revokes the first")
  void oneLiveTokenPerPod() {
    IssuedPodToken first = tokens.mint(POD, Duration.ofHours(1));
    IssuedPodToken second = tokens.mint(POD, Duration.ofHours(1));

    assertThat(tokens.isLive(first.jti(), POD)).isFalse();
    assertThat(tokens.isLive(second.jti(), POD)).isTrue();
  }

  @Test
  @DisplayName("two mints for one pod at once still leave exactly one live token")
  void concurrentMints() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<IssuedPodToken>> mints = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        mints.add(() -> tokens.mint(POD, Duration.ofHours(1)));
      }
      List<IssuedPodToken> issued = new ArrayList<>();
      for (Future<IssuedPodToken> mint : pool.invokeAll(mints)) {
        issued.add(mint.get());
      }
      assertThat(issued.stream().filter(t -> tokens.isLive(t.jti(), POD))).hasSize(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM gpu_pod_tokens WHERE revoked_at IS NULL", Integer.class))
          .isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName(
      "the sweep revokes the token of every pod that no longer runs, and keeps a live pod's")
  void sweep() {
    IssuedPodToken running = tokens.mint(POD, Duration.ofHours(1));
    IssuedPodToken ended = tokens.mint(OTHER, Duration.ofHours(1));

    assertThat(tokens.sweep(List.of(POD))).containsExactly(OTHER);
    assertThat(tokens.isLive(running.jti(), POD)).isTrue();
    assertThat(tokens.isLive(ended.jti(), OTHER)).isFalse();
    assertThat(tokens.sweep(List.of())).containsExactly(POD);
  }

  @Test
  @DisplayName("rows are purged a day after their expiry: the table stays bounded")
  void purge() {
    tokens.mint(POD, Duration.ofHours(1));
    clock.set(PodTokenTestWiring.T0.plus(Duration.ofHours(24)));
    assertThat(tokens.purge()).as("expired 23 h ago: kept").isZero();
    clock.set(PodTokenTestWiring.T0.plus(Duration.ofHours(26)));
    assertThat(tokens.purge()).isEqualTo(1);
  }
}
