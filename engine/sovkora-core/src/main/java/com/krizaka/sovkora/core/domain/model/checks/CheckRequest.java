package com.krizaka.sovkora.core.domain.model.checks;

import java.util.Objects;

/**
 * What a connector receives: the subject and the key already resolved for this call.
 *
 * @param subject the person checked
 * @param key the provider key to call with
 * @param endpoint the provider's endpoint from its admin row, or {@code null} (a connector that
 *     needs one answers "not configured" without it)
 */
public record CheckRequest(CheckSubject subject, CheckKey key, String endpoint) {

  /**
   * A request with no endpoint (the mock's).
   *
   * @param subject the person checked
   * @param key the provider key
   */
  public CheckRequest(CheckSubject subject, CheckKey key) {
    this(subject, key, null);
  }

  /** Validates the request. */
  public CheckRequest {
    Objects.requireNonNull(subject, "subject must not be null");
    Objects.requireNonNull(key, "key must not be null");
  }
}
