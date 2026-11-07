package com.krizaka.sovkora.knowledgeservice.recommendation.application.service;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Candidate;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Feed;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.InteractionKind;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.RankedItem;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Reason;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.RecommendationSettings;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.RecommendationsDisabledException;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Strategy;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.UnknownItemException;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.UserProfile;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.port.RecommendationRepository;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.port.RecommendationRepository.ItemFacts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Serving (ADR-078 §4, §6): retrieval from five sources — nearest to the profile, co-engagement,
 * fresh, followed creators, popular — then {@link RankingService}. The product's access rules are
 * the product's: it sent only eligible items and re-filters every answer.
 */
@Service
public class FeedService {

  private static final Logger logger = LoggerFactory.getLogger(FeedService.class);

  private final RecommendationRepository repository;
  private final RecommendationSettingsService settings;
  private final RankingService ranking;
  private final Clock clock;

  public FeedService(
      RecommendationRepository repository,
      RecommendationSettingsService settings,
      RankingService ranking,
      Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.ranking = Objects.requireNonNull(ranking, "ranking");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** "For you": the person's feed. */
  public Feed forYou(String tenant, String userId, int limit, Set<String> exclude) {
    long started = System.nanoTime();
    Instant now = clock.instant();
    RecommendationSettings s = enabled(tenant);
    int pool = s.candidatePool();

    Set<String> excluded = new HashSet<>(exclude);
    if (s.excludeSeen()) {
      excluded.addAll(repository.engagedItems(tenant, userId));
    }
    Set<String> blockedCreators = repository.reportedCreators(tenant, userId);
    Optional<UserProfile> profile = repository.profile(tenant, userId);
    Set<String> follows = repository.followedCreators(tenant, userId);

    Map<String, EnumSet<Reason>> sources = new LinkedHashMap<>();
    Map<String, Double> similarity = Map.of();
    if (profile.isPresent()) {
      similarity = repository.nearest(tenant, profile.get().vector(), pool);
      similarity.keySet().forEach(id -> source(sources, id, Reason.SIMILAR));
    }
    Map<String, Double> co = repository.coEngaged(tenant, userId, s.positiveKinds(), pool);
    co.keySet().forEach(id -> source(sources, id, Reason.CO_ENGAGED));
    if (!follows.isEmpty()) {
      repository
          .itemsOfCreators(tenant, follows, pool)
          .forEach(id -> source(sources, id, Reason.FOLLOWED));
    }
    repository.freshest(tenant, pool).forEach(id -> source(sources, id, Reason.FRESH));
    Map<String, Double> popular = popularity(tenant, s, now, pool);
    popular.keySet().forEach(id -> source(sources, id, Reason.POPULAR));

    sources.keySet().removeAll(excluded);
    List<Candidate> candidates = new ArrayList<>(sources.size());
    for (ItemFacts facts : repository.itemFacts(tenant, sources.keySet())) {
      if (blockedCreators.contains(facts.creatorId())) {
        continue;
      }
      double sim =
          profile.isPresent() && facts.vector() != null
              ? facts.vector().cosine(profile.get().vector())
              : 0;
      candidates.add(
          new Candidate(
              facts.itemId(),
              facts.creatorId(),
              facts.publishedAt(),
              facts.vector(),
              sim,
              co.getOrDefault(facts.itemId(), 0.0),
              popular.getOrDefault(facts.itemId(), 0.0),
              follows.contains(facts.creatorId()),
              sources.get(facts.itemId())));
    }
    boolean personal = profile.isPresent() || !co.isEmpty() || !follows.isEmpty();
    Strategy strategy = personal ? Strategy.PERSONAL : Strategy.COLD_START;
    List<RankedItem> items = ranking.rank(candidates, s, limit, now);
    return served(tenant, userId, "FOR_YOU", strategy, candidates.size(), items, now, started);
  }

  /** "More like this": items near an item. */
  public Feed similar(String tenant, String itemId, int limit, Set<String> exclude) {
    long started = System.nanoTime();
    Instant now = clock.instant();
    RecommendationSettings s = enabled(tenant);
    ItemFacts source =
        repository
            .item(tenant, itemId)
            .filter(f -> f.vector() != null)
            .orElseThrow(UnknownItemException::new);
    Map<String, Double> near =
        new LinkedHashMap<>(repository.nearest(tenant, source.vector(), s.candidatePool()));
    near.remove(itemId);
    near.keySet().removeAll(exclude);
    Map<String, Double> popular = popularity(tenant, s, now, s.candidatePool());
    List<Candidate> candidates = new ArrayList<>(near.size());
    for (ItemFacts facts : repository.itemFacts(tenant, near.keySet())) {
      EnumSet<Reason> why = EnumSet.of(Reason.SIMILAR);
      if (popular.containsKey(facts.itemId())) {
        why.add(Reason.POPULAR);
      }
      candidates.add(
          new Candidate(
              facts.itemId(),
              facts.creatorId(),
              facts.publishedAt(),
              facts.vector(),
              near.get(facts.itemId()),
              0,
              popular.getOrDefault(facts.itemId(), 0.0),
              false,
              why));
    }
    List<RankedItem> items = ranking.rank(candidates, s, limit, now);
    return served(
        tenant, null, "SIMILAR", Strategy.SIMILAR, candidates.size(), items, now, started);
  }

  private RecommendationSettings enabled(String tenant) {
    RecommendationSettings s = settings.forTenant(tenant);
    if (!s.enabled()) {
      throw new RecommendationsDisabledException();
    }
    return s;
  }

  /** Weighted positive events of the window, per item. */
  private Map<String, Double> popularity(
      String tenant, RecommendationSettings s, Instant now, int limit) {
    Instant since = now.minus(Duration.ofDays(s.popularWindowDays()));
    List<InteractionKind> positive = s.positiveKinds();
    Map<String, Double> scores = new LinkedHashMap<>();
    repository
        .recentEngagement(tenant, since, positive, limit)
        .forEach(
            (item, counts) -> {
              double score = 0;
              for (Map.Entry<InteractionKind, Long> e : counts.entrySet()) {
                score += s.weightOf(e.getKey()) * e.getValue();
              }
              scores.put(item, score);
            });
    return scores;
  }

  private static void source(Map<String, EnumSet<Reason>> sources, String id, Reason reason) {
    sources.computeIfAbsent(id, k -> EnumSet.noneOf(Reason.class)).add(reason);
  }

  private Feed served(
      String tenant,
      String userId,
      String kind,
      Strategy strategy,
      int candidates,
      List<RankedItem> items,
      Instant now,
      long started) {
    UUID requestId = UUID.randomUUID();
    repository.logRequest(
        requestId,
        tenant,
        userId,
        kind,
        strategy.name(),
        items.stream().map(RankedItem::itemId).toList(),
        now);
    // Ids and counts only (LOG-001): never a title, a tag or a vector.
    logger.info(
        "recommendation.served requestId={} tenant={} kind={} strategy={} candidates={} returned={} ms={}",
        requestId,
        tenant,
        kind,
        strategy,
        candidates,
        items.size(),
        (System.nanoTime() - started) / 1_000_000);
    return new Feed(requestId, strategy, candidates, items);
  }
}
