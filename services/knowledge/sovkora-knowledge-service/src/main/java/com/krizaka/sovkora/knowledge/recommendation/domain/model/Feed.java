package com.krizaka.sovkora.knowledge.recommendation.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A served list: what the product shows, and the id that finds this answer in Sovkora's logs.
 *
 * @param requestId the request's id ({@code recommendation.served requestId=…})
 * @param strategy how it was made
 * @param candidates how many candidates retrieval brought before ranking
 * @param items the items, in order
 */
public record Feed(UUID requestId, Strategy strategy, int candidates, List<RankedItem> items) {

  public Feed {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(strategy, "strategy");
    items = items == null ? List.of() : List.copyOf(items);
  }
}
