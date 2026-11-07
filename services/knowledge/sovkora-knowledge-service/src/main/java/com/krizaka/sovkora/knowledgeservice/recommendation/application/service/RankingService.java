package com.krizaka.sovkora.knowledgeservice.recommendation.application.service;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Candidate;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.RankedItem;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.RankingWeights;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Reason;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.RecommendationSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Service;

/**
 * Ranking and re-ranking (ADR-078 §4) — a transparent weighted score, then MMR for diversity and a
 * creator cap per page. Every term is normalised to [0, 1] over the candidate set (freshness is an
 * absolute half-life decay), weighted by the tenant's settings, and returned as the item's reasons.
 * Learning-to-rank replaces the weighted score only once logged outcomes exist (deferred).
 */
@Service
public class RankingService {

  /** Ranks the candidates and picks at most {@code limit} of them. */
  public List<RankedItem> rank(
      List<Candidate> candidates, RecommendationSettings settings, int limit, Instant now) {
    if (candidates.isEmpty() || limit <= 0) {
      return List.of();
    }
    RankingWeights w = settings.weights();
    Normaliser similarity = Normaliser.over(candidates, Candidate::similarity);
    Normaliser co = Normaliser.over(candidates, Candidate::coEngagement);
    Normaliser popularity = Normaliser.over(candidates, Candidate::popularity);

    List<Scored> scored = new ArrayList<>(candidates.size());
    for (Candidate c : candidates) {
      Map<Reason, Double> terms = new EnumMap<>(Reason.class);
      terms.put(Reason.SIMILAR, w.similarity() * similarity.apply(c.similarity()));
      terms.put(Reason.CO_ENGAGED, w.coEngagement() * co.apply(c.coEngagement()));
      terms.put(Reason.FRESH, w.freshness() * freshness(c.publishedAt(), now, settings));
      terms.put(Reason.FOLLOWED, c.followed() ? w.followed() : 0.0);
      terms.put(Reason.POPULAR, w.popularity() * popularity.apply(c.popularity()));
      double score = terms.values().stream().mapToDouble(Double::doubleValue).sum();
      List<Reason> reasons =
          terms.entrySet().stream()
              .filter(e -> e.getValue() > 0 && c.sources().contains(e.getKey()))
              .sorted(Map.Entry.<Reason, Double>comparingByValue().reversed())
              .map(Map.Entry::getKey)
              .toList();
      scored.add(new Scored(c, score, reasons));
    }
    scored.sort(Comparator.comparingDouble(Scored::score).reversed());
    return diversify(scored, settings, limit);
  }

  /** {@code 2^(−age / freshHalfLife)}: 1 for an item published now. */
  static double freshness(Instant publishedAt, Instant now, RecommendationSettings settings) {
    double ageHours = Math.max(0, Duration.between(publishedAt, now).toMinutes() / 60.0);
    return Math.pow(2, -ageHours / settings.freshHalfLifeHours());
  }

  /**
   * Maximal marginal relevance with a creator cap: each pick maximises {@code λ·score − (1−λ)·max
   * cos(item, picked)}; a creator at the cap is skipped. Stops early rather than break the cap —
   * the product fills the rest of the page from its own shelves.
   */
  private static List<RankedItem> diversify(
      List<Scored> pool, RecommendationSettings settings, int limit) {
    double lambda = settings.mmrLambda();
    List<Scored> remaining = new ArrayList<>(pool);
    List<Scored> picked = new ArrayList<>();
    Map<String, Integer> perCreator = new HashMap<>();
    while (picked.size() < limit && !remaining.isEmpty()) {
      Scored best = null;
      double bestValue = Double.NEGATIVE_INFINITY;
      for (Scored s : remaining) {
        if (perCreator.getOrDefault(s.candidate().creatorId(), 0) >= settings.creatorCap()) {
          continue;
        }
        double redundancy = 0;
        for (Scored p : picked) {
          if (s.candidate().vector() != null && p.candidate().vector() != null) {
            redundancy =
                Math.max(redundancy, s.candidate().vector().cosine(p.candidate().vector()));
          }
        }
        double value = lambda * s.score() - (1 - lambda) * redundancy;
        if (value > bestValue) {
          bestValue = value;
          best = s;
        }
      }
      if (best == null) {
        break;
      }
      remaining.remove(best);
      picked.add(best);
      perCreator.merge(best.candidate().creatorId(), 1, Integer::sum);
    }
    return picked.stream()
        .map(
            s ->
                new RankedItem(
                    s.candidate().itemId(),
                    s.candidate().creatorId(),
                    Math.round(s.score() * 10_000) / 10_000.0,
                    s.reasons()))
        .toList();
  }

  private record Scored(Candidate candidate, double score, List<Reason> reasons) {}

  /** Min–max normalisation over the candidate set; a flat set normalises to 1 when positive. */
  private record Normaliser(double min, double max) {

    static Normaliser over(List<Candidate> candidates, ToDoubleFunction<Candidate> value) {
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (Candidate c : candidates) {
        double v = value.applyAsDouble(c);
        min = Math.min(min, v);
        max = Math.max(max, v);
      }
      return new Normaliser(Math.max(0, min), Math.max(0, max));
    }

    double apply(double value) {
      double v = Math.max(0, value);
      if (max <= 0) {
        return 0;
      }
      if (max == min) {
        return v > 0 ? 1 : 0;
      }
      return Math.max(0, (v - min) / (max - min));
    }
  }
}
