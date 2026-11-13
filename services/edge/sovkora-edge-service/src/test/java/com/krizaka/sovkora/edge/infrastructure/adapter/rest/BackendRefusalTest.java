package com.krizaka.sovkora.edge.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class BackendRefusalTest {

  @Test
  @DisplayName("A refusal is a 503 problem with a stable code and the Retry-After in its body too")
  void refusalIsACodedProblem() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    BackendRefusal.TIMEOUT.write(response, Duration.ofSeconds(7));

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getHeader("Retry-After")).isEqualTo("7");
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"type\":\"about:blank\",\"title\":\"Service Unavailable\",\"status\":503,"
                + "\"code\":\"BACKEND_TIMEOUT\",\"detail\":\"The service behind the edge did not"
                + " answer in time. Retry after the delay.\",\"error\":\"Service Unavailable\","
                + "\"retryAfterSeconds\":7}");
  }

  @Test
  @DisplayName("Retry-After is rounded up to whole seconds, and never below one")
  void retryAfterIsRoundedUp() {
    assertThat(retryAfter(Duration.ofMillis(1500))).isEqualTo("2");
    assertThat(retryAfter(Duration.ofSeconds(30))).isEqualTo("30");
    assertThat(retryAfter(Duration.ZERO)).isEqualTo("1");
    assertThat(BackendRefusal.UNAVAILABLE.code()).isEqualTo("BACKEND_UNAVAILABLE");
  }

  private static String retryAfter(Duration delay) {
    MockHttpServletResponse response = new MockHttpServletResponse();
    try {
      BackendRefusal.UNAVAILABLE.write(response, delay);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    return response.getHeader("Retry-After");
  }
}
