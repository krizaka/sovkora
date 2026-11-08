package com.krizaka.sovkora.knowledge.recommendation.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.knowledge.recommendation.domain.model.CatalogueItem;
import java.time.Instant;
import java.util.List;

/**
 * One eligible item, public metadata only.
 *
 * @param id the item
 * @param creator its creator
 * @param title its title
 * @param description its description
 * @param tags its tags
 * @param language BCP-47
 * @param durationSeconds its duration
 * @param publishedAt when it became available
 */
public record ItemRequest(
    String id,
    String creator,
    String title,
    String description,
    List<String> tags,
    String language,
    Integer durationSeconds,
    Instant publishedAt) {

  /** The validated item. */
  public CatalogueItem toItem() {
    return new CatalogueItem(
        id, creator, title, description, tags, language, durationSeconds, publishedAt);
  }
}
