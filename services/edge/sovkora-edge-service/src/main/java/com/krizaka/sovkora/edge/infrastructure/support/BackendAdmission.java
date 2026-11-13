package com.krizaka.sovkora.edge.infrastructure.support;

import java.time.Duration;

/** The breaker's answer to a request to call a backend. */
public sealed interface BackendAdmission {

  /** The call may go; it reports its outcome to {@code call}. */
  record Granted(BackendCall call) implements BackendAdmission {}

  /** The circuit is open; the caller may come back after {@code retryAfter}. */
  record Refused(Duration retryAfter) implements BackendAdmission {}
}
