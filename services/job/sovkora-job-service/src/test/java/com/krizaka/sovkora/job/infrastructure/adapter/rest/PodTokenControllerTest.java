package com.krizaka.sovkora.job.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.job.application.service.PodTokenService;
import com.krizaka.sovkora.job.domain.model.IssuedPodToken;
import com.krizaka.sovkora.job.domain.model.PodKey;
import com.krizaka.sovkora.job.infrastructure.adapter.rest.dto.PodSweepRequest;
import com.krizaka.sovkora.job.infrastructure.adapter.rest.dto.PodTokenRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PodTokenControllerTest {

  private static final PodKey POD = new PodKey("20261114180000-a1b2c3");

  private final PodTokenService tokens = mock(PodTokenService.class);
  private final PodTokenController controller = new PodTokenController(tokens);

  @Test
  @DisplayName("mint asks for the pod's lifetime cap and answers the token, its id and its expiry")
  void mint() {
    UUID jti = UUID.randomUUID();
    Instant exp = Instant.parse("2026-11-15T02:30:00Z");
    when(tokens.mint(POD, Duration.ofHours(8)))
        .thenReturn(new IssuedPodToken("jws", jti, POD, exp));

    var response = controller.mint(POD.value(), new PodTokenRequest(8 * 3600));

    assertThat(response.token()).isEqualTo("jws");
    assertThat(response.jti()).isEqualTo(jti.toString());
    assertThat(response.expiresAt()).isEqualTo(exp);
    assertThat(response.toString()).doesNotContain("jws");
  }

  @Test
  @DisplayName("revoke and sweep answer the pods whose token they ended")
  void revokeAndSweep() {
    when(tokens.revoke(POD)).thenReturn(1);
    assertThat(controller.revoke(POD.value()).revoked()).containsExactly(POD.value());
    when(tokens.sweep(anyList())).thenReturn(List.of(POD));
    assertThat(controller.sweep(new PodSweepRequest(List.of())).revoked())
        .containsExactly(POD.value());
    verify(tokens).sweep(List.of());
  }

  @Test
  @DisplayName("a malformed key or lifetime is a 400, never a 500")
  void malformed() {
    assertThatThrownBy(() -> controller.revoke("gpu-worker"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PodTokenRequest(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PodSweepRequest(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(controller.malformed(new IllegalArgumentException("x")).getStatus()).isEqualTo(400);
  }
}
