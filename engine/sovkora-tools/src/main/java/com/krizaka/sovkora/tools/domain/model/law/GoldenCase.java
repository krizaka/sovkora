package com.krizaka.sovkora.tools.domain.model.law;

import java.util.Locale;
import java.util.Objects;

/**
 * One golden case of a kind in the pack ({@code worker/fixtures/<kindId>/<name>.txt}): a whole
 * fictional document and the verdicts its ruleset must give it. The pack names its intent: {@code
 * compliant}, {@code defective}, {@code blank}.
 *
 * @param name the file name without {@code .txt}, such as {@code fr-compliant}
 */
public record GoldenCase(String name) {

  /** Checks the name. */
  public GoldenCase {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("a golden case has a name");
    }
  }

  /**
   * Whether it is the kind's blank form (expected: no PASS).
   *
   * @return true for a blank form
   */
  public boolean blank() {
    return lower().contains("blank");
  }

  /**
   * Whether it is a clean document.
   *
   * @return true for a compliant one
   */
  public boolean compliant() {
    return lower().contains("compliant");
  }

  /**
   * Whether it is a document with known errors.
   *
   * @return true for a defective one
   */
  public boolean defective() {
    return lower().contains("defective");
  }

  private String lower() {
    return name.toLowerCase(Locale.ROOT);
  }
}
