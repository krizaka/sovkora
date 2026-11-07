package com.krizaka.sovkora.engine.tools.domain.model.search;

import java.util.Objects;

/** Response payload for the searchKnowledge tool. */
public record SearchWebResponse(String results, boolean success) {
  public SearchWebResponse {
    Objects.requireNonNull(results, "results must not be null");
  }
}
