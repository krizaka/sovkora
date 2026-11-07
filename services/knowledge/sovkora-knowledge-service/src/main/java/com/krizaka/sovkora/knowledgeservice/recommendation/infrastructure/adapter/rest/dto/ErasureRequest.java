package com.krizaka.sovkora.knowledgeservice.recommendation.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Identifiers;
import java.util.List;

/**
 * People and items to forget (an account deleted, an item out of the product's discovery).
 *
 * @param users pseudonyms
 * @param items item ids
 */
public record ErasureRequest(List<String> users, List<String> items) {

  /** The most of each per request. */
  public static final int MAX = 500;

  public ErasureRequest {
    users = users == null ? List.of() : users.stream().map(Identifiers::requirePseudonym).toList();
    items =
        items == null
            ? List.of()
            : items.stream().map(i -> Identifiers.require(i, "items")).toList();
    if (users.size() > MAX || items.size() > MAX) {
      throw new IllegalArgumentException("at most " + MAX + " users and " + MAX + " items");
    }
    if (users.isEmpty() && items.isEmpty()) {
      throw new IllegalArgumentException("nothing to erase");
    }
  }
}
