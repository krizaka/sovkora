package com.krizaka.sovkora.edge.infrastructure.support;

import java.util.Arrays;
import java.util.Objects;

/**
 * The surfaces a client calls through the edge, the only paths it forwards
 * (krizaka-restricted#348): the API and the signed inbound webhooks. A backend's {@code
 * /actuator/**}, {@code /internal/**} or any other path is never reached from outside, whatever the
 * route table maps; the edge answers its own health at {@code /healthz}.
 */
public enum ClientSurface {
  /** The public API: a session token or an {@code oz_} key, verified by the owning service. */
  API("/api/"),
  /** The signed inbound webhooks (ADR-084): the automation service verifies the signature. */
  HOOKS("/hooks/");

  private final String prefix;

  ClientSurface(String prefix) {
    this.prefix = prefix;
  }

  /** Whether the edge forwards a request for this path: it lies under one client surface. */
  public static boolean forwards(String path) {
    Objects.requireNonNull(path, "path cannot be null");
    return Arrays.stream(values()).anyMatch(surface -> path.startsWith(surface.prefix));
  }
}
