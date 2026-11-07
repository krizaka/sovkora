package com.krizaka.sovkora.tools.domain.model.search;

import java.util.Objects;

/** Request payload for the searchKnowledge tool. */
public record SearchWebRequest(String query) {
  public SearchWebRequest {
    Objects.requireNonNull(query, "query must not be null");
    if (query.isBlank()) {
      throw new IllegalArgumentException("query must not be blank");
    }
  }
}
