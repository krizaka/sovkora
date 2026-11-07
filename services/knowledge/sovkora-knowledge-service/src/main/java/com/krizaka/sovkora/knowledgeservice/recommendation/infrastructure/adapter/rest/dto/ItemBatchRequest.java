package com.krizaka.sovkora.knowledgeservice.recommendation.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.CatalogueItem;
import java.util.List;

/**
 * A batch of items (at most {@value #MAX}).
 *
 * @param items the items
 */
public record ItemBatchRequest(List<ItemRequest> items) {

  /** The largest batch. */
  public static final int MAX = 100;

  /** The validated items. */
  public List<CatalogueItem> toItems() {
    if (items == null || items.isEmpty() || items.size() > MAX) {
      throw new IllegalArgumentException("items: 1 to " + MAX + " per batch");
    }
    return items.stream().map(ItemRequest::toItem).toList();
  }
}
