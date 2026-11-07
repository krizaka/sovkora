package com.krizaka.sovkora.automationservice.domain.model;

import java.util.Locale;

/** What to do with slots missed while the service was down (ADR-079 §1). */
public enum CatchUp {
  /** Run once when back, for the latest missed slot only. */
  ONCE,
  /** Do nothing for missed slots; wait for the next one. */
  SKIP;

  /**
   * Reads the wire form, case-insensitively; absent means {@link #ONCE}.
   *
   * @param value {@code once}, {@code skip} or null
   * @return the policy
   * @throws IllegalArgumentException when it is anything else
   */
  public static CatchUp parse(String value) {
    if (value == null) {
      return ONCE;
    }
    try {
      return valueOf(value.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException("unknown catch-up; choose once or skip");
    }
  }
}
