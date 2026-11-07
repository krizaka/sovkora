package com.krizaka.sovkora.knowledge.analytics.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A kind of event a tenant may send — a row of {@code analytics_event_kind}, never a constant in
 * code (ADR-081 §2).
 *
 * @param kind the kind's name, lower snake case
 * @param unit what its value measures
 * @param source the endpoint that receives it
 */
public record EventKind(String kind, ValueUnit unit, KindSource source) {

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{1,31}");

  public EventKind {
    kind = requireName(kind);
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(source, "source");
  }

  /** The kind's name, or an {@link IllegalArgumentException}. */
  public static String requireName(String kind) {
    if (kind == null || !NAME.matcher(kind).matches()) {
      throw new IllegalArgumentException(
          "kind must be 2-32 characters of [a-z0-9_], a letter first");
    }
    return kind;
  }
}
