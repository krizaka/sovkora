package com.krizaka.sovkora.edge.infrastructure.config.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Every refusal of a key at the facade is an RFC 9457 problem with a stable code. */
class ApiKeyRefusalTest {

  @Test
  @DisplayName("[2132] a refusal is application/problem+json with its status, code and title")
  void writesAProblem() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    ApiKeyRefusal.QUOTA_EXCEEDED.write(response, "Come back after 00:00 UTC.");

    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    assertThat(response.getContentAsString())
        .contains("\"type\":\"about:blank\"")
        .contains("\"status\":429")
        .contains("\"code\":\"API_KEY_QUOTA_EXCEEDED\"")
        .contains("\"title\":\"Daily quota of this API key is reached\"")
        .contains("\"detail\":\"Come back after 00:00 UTC.\"")
        // The field earlier callers read stays.
        .contains("\"error\":\"Daily quota of this API key is reached\"");
  }

  @Test
  @DisplayName("[2132] a rate-limit refusal also carries retryAfterSeconds")
  void carriesRetryAfter() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    ApiKeyRefusal.RATE_LIMITED.write(response, "Slow down.", 30);

    assertThat(response.getContentAsString())
        .contains("\"code\":\"API_KEY_RATE_LIMITED\"")
        .contains("\"retryAfterSeconds\":30");
  }

  @Test
  @DisplayName("[2132] every code is unique and names an API-key refusal")
  void codesAreUnique() {
    assertThat(Arrays.stream(ApiKeyRefusal.values()).map(ApiKeyRefusal::code))
        .doesNotHaveDuplicates()
        .allMatch(code -> code.startsWith("API_KEY_"));
  }
}
