package com.krizaka.sovkora.edge.infrastructure.support;

import java.net.URI;
import java.time.Duration;

/**
 * The circuit breaker of the backends behind the edge (krizaka-internal#2132): one circuit per
 * backend, which stops the edge from calling a backend that keeps failing or answering too slowly,
 * so a caller gets a coded 503 at once instead of a request that hangs.
 */
public interface BackendBreaker {

  /**
   * Asks to call a backend: granted with a call that must report its outcome, or refused while its
   * circuit is open.
   *
   * @param backend a backend of the route table
   */
  BackendAdmission admit(URI backend);

  /**
   * When a caller should try this backend again: the circuit's remaining open time, or a default.
   */
  Duration retryAfter(URI backend);
}
