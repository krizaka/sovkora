package com.krizaka.sovkora.tools.domain.model.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One path for a policy to compare: unreserved escapes decoded, reserved ones kept (RFC 3986). */
class UriPathsTest {

  @Test
  @DisplayName("%70rivate is private; %2F stays an escaped slash; other escapes are upper-cased")
  void canonicalForm() {
    assertThat(UriPaths.canonical("/%70rivate/%73ecret")).isEqualTo("/private/secret");
    assertThat(UriPaths.canonical("/a%2Fb/%7e%2d")).isEqualTo("/a%2Fb/~-");
    assertThat(UriPaths.canonical("/caf%c3%a9")).isEqualTo("/caf%C3%A9");
    assertThat(UriPaths.canonical("/%zz/%4")).isEqualTo("/%zz/%4");
  }

  @Test
  @DisplayName("a URI's path and query, / when empty")
  void fromUri() {
    assertThat(UriPaths.canonical(URI.create("https://a.test"))).isEqualTo("/");
    assertThat(UriPaths.canonical(URI.create("https://a.test/%70age?q=%41&x=%2F")))
        .isEqualTo("/page?q=A&x=%2F");
  }
}
