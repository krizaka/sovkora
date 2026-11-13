package com.krizaka.sovkora.edge.infrastructure.config.filter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Every way the facade refuses an {@code oz_} key, each an RFC 9457 problem with a stable {@code
 * code} a calling product can branch on (krizaka-internal#2132). The {@code error} field the edge
 * answered before stays, with the same text, for the callers that read it.
 */
enum ApiKeyRefusal {
  INVALID(401, "API_KEY_INVALID", "Invalid API key"),
  WRONG_ENVIRONMENT(401, "API_KEY_WRONG_ENVIRONMENT", "API key of another environment"),
  PRODUCT_INVALID(401, "API_KEY_PRODUCT_INVALID", "API key declares an invalid product"),
  SCOPE_DENIED(403, "API_KEY_SCOPE_DENIED", "API key scope does not allow this request"),
  RATE_LIMITED(429, "API_KEY_RATE_LIMITED", "Rate limit of this API key is reached"),
  QUOTA_EXCEEDED(429, "API_KEY_QUOTA_EXCEEDED", "Daily quota of this API key is reached"),
  EXCHANGE_UNREACHABLE(502, "API_KEY_EXCHANGE_UNREACHABLE", "Bad Gateway");

  private static final String PROBLEM_JSON = "application/problem+json";

  private final int status;
  private final String code;
  private final String title;

  ApiKeyRefusal(int status, String code, String title) {
    this.status = status;
    this.code = code;
    this.title = title;
  }

  String code() {
    return code;
  }

  /** Answers the refusal; {@code detail} is a fixed sentence of the edge, never a caller value. */
  void write(HttpServletResponse response, String detail) throws IOException {
    writeBody(response, detail, "");
  }

  /** Answers a refusal that says when to come back. */
  void write(HttpServletResponse response, String detail, long retryAfterSeconds)
      throws IOException {
    writeBody(response, detail, ",\"retryAfterSeconds\":" + retryAfterSeconds);
  }

  private void writeBody(HttpServletResponse response, String detail, String extra)
      throws IOException {
    response.setStatus(status);
    response.setContentType(PROBLEM_JSON);
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
                + "\""
                + extra
                + "}");
  }
}
