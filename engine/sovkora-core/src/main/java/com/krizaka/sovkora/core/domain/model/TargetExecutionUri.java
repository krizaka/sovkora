package com.krizaka.sovkora.core.domain.model;

import java.util.Objects;

/**
 * Carries coordinates for executing a specific graph operation endpoint.
 *
 * @param uriPath The target REST path pattern or endpoint URL.
 * @param httpMethod The HTTP method (GET, POST, etc.) for invocation.
 * @param payloadTemplate Optional template payload matching expected JSON formats.
 */
public final record TargetExecutionUri(String uriPath, String httpMethod, String payloadTemplate) {
  public TargetExecutionUri {
    Objects.requireNonNull(uriPath, "URI path cannot be null");
    if (uriPath.isBlank()) {
      throw new IllegalArgumentException("URI path cannot be blank");
    }
    Objects.requireNonNull(httpMethod, "HTTP method cannot be null");
    if (httpMethod.isBlank()) {
      throw new IllegalArgumentException("HTTP method cannot be blank");
    }
  }
}
