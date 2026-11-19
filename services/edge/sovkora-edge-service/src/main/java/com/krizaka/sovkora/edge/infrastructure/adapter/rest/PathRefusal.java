package com.krizaka.sovkora.edge.infrastructure.adapter.rest;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * How the edge answers a path it will not forward (krizaka-internal#4361): a 400 RFC 9457 problem
 * with the stable code {@code PATH_NOT_NORMAL}, the same shape as {@link BackendRefusal}. The
 * {@code detail} is a fixed sentence and never echoes the path.
 */
final class PathRefusal {

  static final String CODE = "PATH_NOT_NORMAL";
  private static final int STATUS = 400;
  private static final String TITLE = "Bad Request";
  private static final String DETAIL =
      "The path holds a dot-segment, an encoded separator or a character outside RFC 3986.";

  private PathRefusal() {}

  /** Answers the refusal. */
  static void write(HttpServletResponse response) throws IOException {
    response.setStatus(STATUS);
    response.setContentType("application/problem+json");
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + TITLE
                + "\",\"status\":"
                + STATUS
                + ",\"code\":\""
                + CODE
                + "\",\"detail\":\""
                + DETAIL
                + "\",\"error\":\""
                + TITLE
                + "\"}");
  }
}
