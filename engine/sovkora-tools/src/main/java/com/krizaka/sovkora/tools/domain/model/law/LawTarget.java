package com.krizaka.sovkora.tools.domain.model.law;

import java.util.ArrayList;
import java.util.List;

/**
 * The articles of one instrument the watcher follows.
 *
 * @param jurisdiction {@code CA} or {@code QC}
 * @param instrument the instrument code
 * @param refs the article references followed, expanded from ranges; empty means the whole
 *     instrument
 */
public record LawTarget(String jurisdiction, String instrument, List<String> refs) {

  public LawTarget {
    refs = List.copyOf(refs);
  }

  /**
   * A target that follows every article of the instrument, as {@code law.import.instruments} does.
   *
   * @param jurisdiction the jurisdiction
   * @param instrument the instrument code
   * @return the target
   */
  public static LawTarget wholeInstrument(String jurisdiction, String instrument) {
    return new LawTarget(jurisdiction, instrument, List.of());
  }

  /**
   * Whether an article of this instrument is followed.
   *
   * @param ref the article reference
   * @return true for every article of a whole-instrument target, else when the reference is listed
   */
  public boolean covers(String ref) {
    return refs.isEmpty() || refs.contains(ref);
  }

  /**
   * Builds a target from reference specifications: {@code "1942"} or a numeric range {@code
   * "1851-2000"}.
   *
   * @param jurisdiction the jurisdiction
   * @param instrument the instrument code
   * @param specs the specifications, in any number
   * @return the target with every range expanded, in order and without duplicates
   */
  public static LawTarget of(String jurisdiction, String instrument, List<String> specs) {
    List<String> refs = new ArrayList<>();
    for (String spec : specs) {
      String trimmed = spec.trim();
      int dash = trimmed.indexOf('-', 1);
      if (dash > 0
          && trimmed.substring(0, dash).matches("\\d+")
          && trimmed.substring(dash + 1).matches("\\d+")) {
        int from = Integer.parseInt(trimmed.substring(0, dash));
        int to = Integer.parseInt(trimmed.substring(dash + 1));
        if (to < from) {
          throw new IllegalArgumentException("empty range " + trimmed + " for " + instrument);
        }
        for (int n = from; n <= to; n++) {
          refs.add(Integer.toString(n));
        }
      } else {
        refs.add(trimmed);
      }
    }
    return new LawTarget(jurisdiction, instrument, refs.stream().distinct().toList());
  }
}
