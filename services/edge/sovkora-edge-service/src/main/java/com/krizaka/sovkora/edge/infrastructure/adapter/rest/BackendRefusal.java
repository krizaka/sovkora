package com.krizaka.sovkora.edge.infrastructure.adapter.rest;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

/**
 * How the edge answers when the backend behind a route cannot serve (krizaka-internal#2132): a 503
 * RFC 9457 problem with a stable {@code code} a calling product can branch on, and a {@code
 * Retry-After} in seconds. The same shape as the key refusals; the {@code detail} is a fixed
 * sentence of the edge, never the backend's text.
 */
enum BackendRefusal {
  /** The backend refused the connection, is unreachable, or its circuit is open. */
  UNAVAILABLE(
      "BACKEND_UNAVAILABLE", "The service behind the edge is unavailable. Retry after the delay."),
  /** The backend did not answer within the response timeout. */
  TIMEOUT(
      "BACKEND_TIMEOUT",
      "The service behind the edge did not answer in time. Retry after the delay.");

  private static final int STATUS = 503;
  private static final String TITLE = "Service Unavailable";

  private final String code;
  private final String detail;

  BackendRefusal(String code, String detail) {
    this.code = code;
    this.detail = detail;
  }

  String code() {
    return code;
  }

  /** Answers the refusal; the delay is rounded up to whole seconds, at least one. */
  void write(HttpServletResponse response, Duration retryAfter) throws IOException {
    long seconds = Math.max(1, retryAfter.plusNanos(999_999_999).toSeconds());
    response.setStatus(STATUS);
    response.setHeader("Retry-After", String.valueOf(seconds));
    response.setContentType("application/problem+json");
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + TITLE
                + "\",\"status\":"
                + STATUS
                + ",\"code\":\""
                + code
                + "\",\"detail\":\""
                + detail
                + "\",\"error\":\""
                + TITLE
                + "\",\"retryAfterSeconds\":"
                + seconds
                + "}");
  }
}
