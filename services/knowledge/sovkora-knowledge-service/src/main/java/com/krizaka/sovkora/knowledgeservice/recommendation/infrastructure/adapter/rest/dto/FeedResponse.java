package com.krizaka.sovkora.knowledgeservice.recommendation.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Feed;
import java.util.List;
import java.util.Locale;

/**
 * A served list.
 *
 * @param requestId the id in Sovkora's logs ({@code recommendation.served requestId=…})
 * @param strategy personal · cold_start · similar
 * @param candidates how many candidates were ranked
 * @param items the items, in order
 */
public record FeedResponse(
    String requestId, String strategy, int candidates, List<FeedItem> items) {

  /**
   * One item.
   *
   * @param id the item
   * @param score its weighted score
   * @param reasons why, strongest first: similar · co_engaged · fresh · followed · popular
   */
  public record FeedItem(String id, double score, List<String> reasons) {}

  public static FeedResponse of(Feed feed) {
    return new FeedResponse(
        feed.requestId().toString(),
        feed.strategy().name().toLowerCase(Locale.ROOT),
        feed.candidates(),
        feed.items().stream()
            .map(
                i ->
                    new FeedItem(
                        i.itemId(),
                        i.score(),
                        i.reasons().stream().map(r -> r.name().toLowerCase(Locale.ROOT)).toList()))
            .toList());
  }
}
