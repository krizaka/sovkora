package com.krizaka.sovkora.automationservice.domain.model;

import java.util.Locale;

/** What starts a cloud automation (ADR-084). The device kinds are the Mac's business (ADR-079). */
public enum TriggerKind {
  /** A signed call from outside. */
  WEBHOOK,
  /** The clock: daily at a time, on chosen weekdays. */
  SCHEDULE;

  /**
   * Reads the wire form, case-insensitively.
   *
   * @param value {@code webhook} or {@code schedule}
   * @return the kind
   * @throws IllegalArgumentException when it is neither
   */
  public static TriggerKind parse(String value) {
    if (value == null) {
      throw new IllegalArgumentException("a trigger needs a kind: webhook or schedule");
    }
    try {
      return valueOf(value.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException("unknown trigger kind; choose webhook or schedule");
    }
  }
}
