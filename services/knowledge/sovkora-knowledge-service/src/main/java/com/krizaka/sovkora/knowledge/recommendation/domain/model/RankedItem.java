package com.krizaka.sovkora.knowledge.recommendation.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * An item of a feed: its score and why it is there, the strongest reason first.
 *
 * @param itemId the item
 * @param creatorId its creator
 * @param score the weighted score, before diversity
 * @param reasons the reasons, strongest contribution first
 */
public record RankedItem(String itemId, String creatorId, double score, List<Reason> reasons) {

  public RankedItem {
    Objects.requireNonNull(itemId, "itemId");
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
  }
}
