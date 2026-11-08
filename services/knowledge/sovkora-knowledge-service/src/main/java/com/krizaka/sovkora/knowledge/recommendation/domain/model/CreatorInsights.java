package com.krizaka.sovkora.knowledge.recommendation.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Creator analytics v1 (ADR-078 §7): what Sovkora can say about a creator from the events it holds
 * — counts and rates only, never a person.
 *
 * @param creatorId the creator
 * @param minAudience the smallest audience a figure is reported for (k-anonymity)
 * @param items each item's engagement, most viewed first
 * @param tags the tags whose items engage best
 * @param bestHours the hours of the week (UTC) when the audience engages most
 * @param alsoWatched creators the audience also engages, with the shared audience size
 */
public record CreatorInsights(
    String creatorId,
    int minAudience,
    List<ItemPerformance> items,
    List<TagPerformance> tags,
    List<HourOfWeek> bestHours,
    List<AlsoWatched> alsoWatched) {

  public CreatorInsights {
    Objects.requireNonNull(creatorId, "creatorId");
    items = List.copyOf(items);
    tags = List.copyOf(tags);
    bestHours = List.copyOf(bestHours);
    alsoWatched = List.copyOf(alsoWatched);
  }

  /**
   * One item's engagement.
   *
   * @param itemId the item
   * @param views views
   * @param engagements positive engagements (like, save, share, tip, unlock)
   * @param skips skips
   * @param engagementRate engagements per view
   * @param avgWatchedPct the mean watched share of the views that reported it, when any did
   */
  public record ItemPerformance(
      String itemId,
      long views,
      long engagements,
      long skips,
      double engagementRate,
      Double avgWatchedPct) {}

  /**
   * A tag's performance across the creator's items.
   *
   * @param tag the tag
   * @param items how many items carry it
   * @param views their views
   * @param engagements their positive engagements
   * @param engagementRate engagements per view
   */
  public record TagPerformance(
      String tag, long items, long views, long engagements, double engagementRate) {}

  /**
   * An hour of the week, in UTC.
   *
   * @param isoDayOfWeek 1 = Monday … 7 = Sunday
   * @param hour 0–23, UTC
   * @param engagements positive engagements in that hour
   */
  public record HourOfWeek(int isoDayOfWeek, int hour, long engagements) {}

  /**
   * Another creator the audience engages.
   *
   * @param creatorId the creator
   * @param sharedAudience how many of this creator's engaged people engaged them too
   */
  public record AlsoWatched(String creatorId, long sharedAudience) {}
}
