package com.krizaka.sovkora.job.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PodTokenDecoderTest {

  private static MockHttpServletRequest bearer(String header) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (header != null) {
      request.addHeader("Authorization", header);
    }
    return request;
  }

  private static String jose(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("only a bearer whose JOSE header names the pod key goes to the pods' chain")
  void routesByKid() {
    String pod = jose("{\"alg\":\"HS256\",\"kid\":\"gpu-pod-v1\"}") + ".e30.sig";
    String service = jose("{\"alg\":\"HS256\",\"typ\":\"JWT\"}") + ".e30.sig";
    assertThat(PodTokenDecoder.carriesPodToken(bearer("Bearer " + pod))).isTrue();
    assertThat(PodTokenDecoder.carriesPodToken(bearer("Bearer " + service))).isFalse();
    assertThat(PodTokenDecoder.carriesPodToken(bearer(null))).isFalse();
    assertThat(PodTokenDecoder.carriesPodToken(bearer("Basic " + pod))).isFalse();
    assertThat(PodTokenDecoder.carriesPodToken(bearer("Bearer not-a-jwt"))).isFalse();
    assertThat(PodTokenDecoder.carriesPodToken(bearer("Bearer %%%.e30.sig"))).isFalse();
  }
}
