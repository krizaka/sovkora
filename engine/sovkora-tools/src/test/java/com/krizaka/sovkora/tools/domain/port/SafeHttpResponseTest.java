package com.krizaka.sovkora.tools.domain.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SafeHttpResponseTest {

  private static SafeHttp.Response response(byte[] body) {
    return new SafeHttp.Response(
        URI.create("https://example.org/"), 200, "text/html", null, body, null, null);
  }

  @Test
  @DisplayName(
      "[#91] responses compare by the content of their body, and print its size, not its bytes")
  void arrayContent() {
    assertThat(response(new byte[] {1, 2, 3})).isEqualTo(response(new byte[] {1, 2, 3}));
    assertThat(response(new byte[] {1, 2, 3}).hashCode())
        .isEqualTo(response(new byte[] {1, 2, 3}).hashCode());
    assertThat(response(new byte[] {1, 2, 3})).isNotEqualTo(response(new byte[] {1, 2, 4}));
    assertThat(response(new byte[] {1, 2, 3}).toString())
        .contains("3 bytes")
        .doesNotContain("[1, 2");
  }
}
