package com.krizaka.sovkora.edge.infrastructure.adapter.rest;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * How the edge answers a path it will not forward: an RFC 9457 problem with a stable {@code code},
 * the same shape as {@link BackendRefusal}. The {@code detail} is a fixed sentence and never echoes
 * the path.
 */
enum PathRefusal {
  /** A path not in normal form (krizaka-internal#4361): the backend could re-normalise it. */
  NOT_NORMAL(
      400,
      "Bad Request",
      "PATH_NOT_NORMAL",
      "The path holds a dot-segment, an encoded separator or a character outside RFC 3986."),
  /** A path outside the client surfaces (krizaka-restricted#348): no backend serves it here. */
  NOT_FORWARDED(404, "Not Found", "ROUTE_NOT_FORWARDED", "The edge serves no such route.");

  private final int status;
  private final String title;
  private final String code;
  private final String detail;

  PathRefusal(int status, String title, String code, String detail) {
    this.status = status;
    this.title = title;
    this.code = code;
    this.detail = detail;
  }

  /** Answers the refusal. */
  void write(HttpServletResponse response) throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + title
                + "\",\"status\":"
                + status
                + ",\"code\":\""
                + code
                + "\",\"detail\":\""
                + detail
                + "\",\"error\":\""
                + title
                + "\"}");
  }
}
