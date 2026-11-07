package com.krizaka.sovkora.core.domain.model.checks;

import java.util.Objects;

/**
 * A resolved provider key, with where it came from. {@link #toString()} never prints the value.
 *
 * @param source whose key it is
 * @param value the key in clear, for the connector only
 */
public record CheckKey(KeySource source, String value) {

  /** Validates the key. */
  public CheckKey {
    Objects.requireNonNull(source, "source must not be null");
    CheckSubject.requireText(value, "value");
  }

  @Override
  public String toString() {
    return "CheckKey[source=" + source + ", value=redacted]";
  }
}
