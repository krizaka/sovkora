package com.krizaka.sovkora.knowledgeservice.recommendation.application.service;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.InteractionKind;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.ProfileSignal;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.RecommendationSettings;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.UserProfile;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Vector;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.port.RecommendationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * User profiles (ADR-078 §4): the decayed, weighted mean of the vectors of the items a person
 * engaged. Each interaction weighs {@code kindWeight × 2^(−age / halfLife)} — a view by the share
 * watched when known — so last week's likes outweigh last year's, and a skip or a report pushes the
 * profile away from what was skipped.
 */
@Service
public class ProfileService {

  /** A view reported as barely watched still says something: its weight never drops below this. */
  private static final double MIN_WATCH_SHARE = 0.1;

  private final RecommendationRepository repository;

  public ProfileService(RecommendationRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  /** Rebuilds the profiles of these people from their latest interactions. */
  public void refresh(
      String tenant, Collection<String> userIds, RecommendationSettings settings, Instant now) {
    for (String userId : userIds) {
      List<ProfileSignal> signals =
          repository.profileSignals(tenant, userId, settings.profileEvents());
      build(signals, settings, now)
          .ifPresentOrElse(
              profile -> repository.saveProfile(tenant, userId, profile, now),
              () -> repository.deleteProfile(tenant, userId));
    }
  }

  /** The profile these signals make, or empty when no positive signal remains. */
  public Optional<UserProfile> build(
      List<ProfileSignal> signals, RecommendationSettings settings, Instant now) {
    Vector sum = Vector.zero();
    int positives = 0;
    for (ProfileSignal signal : signals) {
      double weight = settings.weightOf(signal.kind());
      if (weight == 0) {
        continue;
      }
      if (signal.kind() == InteractionKind.VIEW && signal.watchedPct() != null) {
        weight *= Math.max(MIN_WATCH_SHARE, signal.watchedPct() / 100.0);
      }
      double ageHours = Math.max(0, Duration.between(signal.occurredAt(), now).toMinutes() / 60.0);
      double decay = Math.pow(2, -ageHours / settings.halfLifeHours());
      sum = sum.plus(signal.vector(), weight * decay);
      if (weight > 0) {
        positives++;
      }
    }
    if (positives == 0) {
      return Optional.empty();
    }
    int count = positives;
    return sum.unit().map(unit -> new UserProfile(unit, count));
  }
}
