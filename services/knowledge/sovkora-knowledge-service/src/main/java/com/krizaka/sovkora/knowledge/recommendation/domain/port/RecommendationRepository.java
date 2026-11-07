package com.krizaka.sovkora.knowledge.recommendation.domain.port;

import com.krizaka.sovkora.knowledge.recommendation.domain.model.CatalogueItem;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.CreatorInsights;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.Interaction;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.InteractionKind;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.ProfileSignal;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.RecommendationSettings;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.UserProfile;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.Vector;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The recommender's store (pgvector, {@code orazaka_knowledge_db}). Every method takes the tenant:
 * no query reads across tenants.
 */
public interface RecommendationRepository {

  /**
   * The newest settings version for the tenant, or the default ({@code *}) when the tenant has none
   * — parsed and bounded at the store's edge.
   */
  Optional<RecommendationSettings> settings(String tenant);

  /** Inserts the events; a known id is ignored. Returns how many were new. */
  int insertEvents(String tenant, List<Interaction> events);

  /** The person's most recent interactions with items that have a vector. */
  List<ProfileSignal> profileSignals(String tenant, String userId, int limit);

  /** Saves (or replaces) a profile. */
  void saveProfile(String tenant, String userId, UserProfile profile, Instant at);

  /** Removes a profile (no positive signal left). */
  void deleteProfile(String tenant, String userId);

  Optional<UserProfile> profile(String tenant, String userId);

  /** The content hash of each known item among {@code itemIds}. */
  Map<String, String> contentHashes(String tenant, Collection<String> itemIds);

  /** Inserts or updates an item's facts; {@code vector} null keeps the stored one. */
  void upsertItem(String tenant, CatalogueItem item, String model, Vector vector, Instant at);

  /** Forgets people: their events and profiles. Returns the events deleted. */
  int eraseUsers(String tenant, Collection<String> userIds);

  /** Forgets items: the items and their events. Returns the items deleted. */
  int eraseItems(String tenant, Collection<String> itemIds);

  /** The items the person engaged (any kind). */
  Set<String> engagedItems(String tenant, String userId);

  /** The creators of the items the person reported. */
  Set<String> reportedCreators(String tenant, String userId);

  /** Nearest items to a vector, with their cosine similarity. */
  Map<String, Double> nearest(String tenant, Vector vector, int limit);

  /** Items engaged by the person's peers (people who engaged the same items), with peer counts. */
  Map<String, Double> coEngaged(
      String tenant, String userId, List<InteractionKind> positive, int limit);

  /** The newest items. */
  List<String> freshest(String tenant, int limit);

  /** The creators the person follows now (their latest FOLLOW/UNFOLLOW is a FOLLOW). */
  Set<String> followedCreators(String tenant, String userId);

  /** The newest items of the given creators. */
  List<String> itemsOfCreators(String tenant, Collection<String> creatorIds, int limit);

  /** Per item and kind, the number of events since {@code since}, for the given kinds. */
  Map<String, Map<InteractionKind, Long>> recentEngagement(
      String tenant, Instant since, List<InteractionKind> kinds, int limit);

  /** The facts ranking needs for each item. */
  List<ItemFacts> itemFacts(String tenant, Collection<String> itemIds);

  /** One item's facts, when it is in the catalogue. */
  Optional<ItemFacts> item(String tenant, String itemId);

  /** Records a served request (measurement, ADR-078 §6). */
  void logRequest(
      UUID requestId,
      String tenant,
      String userId,
      String kind,
      String strategy,
      List<String> itemIds,
      Instant at);

  /** Creator analytics v1. */
  CreatorInsights insights(
      String tenant, String creatorId, List<InteractionKind> positive, int minAudience);

  /** Tenants that hold events (for the retention sweep). */
  List<String> tenants();

  /** Deletes the tenant's events older than {@code before}. */
  int purgeEvents(String tenant, Instant before);

  /** Deletes the tenant's served-request log older than {@code before}. */
  int purgeRequests(String tenant, Instant before);

  /**
   * What ranking needs to know about an item.
   *
   * @param itemId the item
   * @param creatorId its creator
   * @param publishedAt when it was published
   * @param vector its embedding, or null
   */
  record ItemFacts(String itemId, String creatorId, Instant publishedAt, Vector vector) {}
}
