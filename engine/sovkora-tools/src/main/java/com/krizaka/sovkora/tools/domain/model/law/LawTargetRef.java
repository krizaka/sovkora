package com.krizaka.sovkora.tools.domain.model.law;

import java.util.List;
import java.util.Objects;

/**
 * The articles of one instrument a kind of document targets (ADR-089 §2): the kind's data names
 * them, the engine never does.
 *
 * @param instrument the instrument code, such as {@code CCQ-1991}
 * @param refs the article references, in the order the kind lists them, such as {@code 1931}
 */
public record LawTargetRef(String instrument, List<String> refs) {

  /** Checks that the instrument and at least one reference are named. */
  public LawTargetRef {
    Objects.requireNonNull(instrument, "instrument");
    if (instrument.isBlank()) {
      throw new IllegalArgumentException("a target names its instrument");
    }
    refs = refs == null ? List.of() : List.copyOf(refs);
    if (refs.isEmpty() || refs.stream().anyMatch(r -> r == null || r.isBlank())) {
      throw new IllegalArgumentException("a target names at least one article, none blank");
    }
  }
}
