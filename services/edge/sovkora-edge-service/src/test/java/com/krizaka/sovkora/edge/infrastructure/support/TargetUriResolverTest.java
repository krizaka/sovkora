package com.krizaka.sovkora.edge.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The edge forwards a path only in its normal form (krizaka-internal#4361, javasecurity:S7044): a
 * path the route table matched on one prefix must reach the backend under that same prefix, never
 * be re-normalised by the backend into another one.
 */
class TargetUriResolverTest {

  private static final URI BACKEND = URI.create("http://backend-job:8090/");

  @Test
  @DisplayName("a normal path and its query are forwarded under the backend's base, unchanged")
  void aNormalPathIsForwarded() {
    assertThat(TargetUriResolver.resolve(BACKEND, "/api/v1/jobs/42/output/a%20b.png", "limit=5"))
        .contains(URI.create("http://backend-job:8090/api/v1/jobs/42/output/a%20b.png?limit=5"));
    assertThat(TargetUriResolver.resolve(BACKEND, "/api/v1/jobs/", null))
        .contains(URI.create("http://backend-job:8090/api/v1/jobs/"));
    assertThat(TargetUriResolver.resolve(BACKEND, "/", null))
        .contains(URI.create("http://backend-job:8090/"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/jobs/../../internal/v1/capabilities",
        "/api/v1/jobs/..",
        "/api/v1/./jobs",
        "/api/v1/jobs/%2e%2e/%2E%2E/internal/v1/capabilities",
        "/api/v1/jobs/.%2e/internal",
        "/api/v1/jobs/%2fetc%2fpasswd",
        "/api/v1/jobs/%5C..%5Cinternal",
        "/api/v1/jobs\\..\\internal",
        "/api/v1/jobs/%00",
        "/api/v1//jobs",
        "/api/v1/jobs/%zz",
        "api/v1/jobs",
        "",
        "/api/v1/jobs/<script>",
        "/api/v1/jobs/a b",
        "/api/v1/jobs/#fragment",
        "/api/v1/jobs/?q"
      })
  @DisplayName("a dot-segment, an encoded separator or a character outside RFC 3986 is refused")
  void aTraversingPathIsRefused(String path) {
    assertThat(TargetUriResolver.resolve(BACKEND, path, null)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"a=1#frag", "a=<b>", "a=1 2", "a=%zz"})
  @DisplayName("a query outside RFC 3986 is refused too")
  void anIllFormedQueryIsRefused(String query) {
    assertThat(TargetUriResolver.resolve(BACKEND, "/api/v1/jobs", query)).isEmpty();
  }

  @Test
  @DisplayName("a query may carry a slash, a question mark and percent-encoded bytes")
  void aWellFormedQueryIsKept() {
    assertThat(TargetUriResolver.resolve(BACKEND, "/api/v1/jobs", "next=/a?b&q=%C3%A9t%C3%A9"))
        .contains(URI.create("http://backend-job:8090/api/v1/jobs?next=/a?b&q=%C3%A9t%C3%A9"));
  }
}
