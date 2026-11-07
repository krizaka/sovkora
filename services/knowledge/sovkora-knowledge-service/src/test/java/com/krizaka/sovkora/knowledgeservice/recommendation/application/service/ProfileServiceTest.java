package com.krizaka.sovkora.knowledgeservice.recommendation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Fixtures;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.InteractionKind;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.ProfileSignal;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.UserProfile;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.port.RecommendationRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileServiceTest {

  private final ProfileService profiles = new ProfileService(mock(RecommendationRepository.class));

  @Test
  @DisplayName("a recent like outweighs an old one: interactions decay with their half-life")
  void decay() {
    List<ProfileSignal> signals =
        List.of(
            new ProfileSignal(InteractionKind.LIKE, Fixtures.NOW, null, Fixtures.axis(1)),
            new ProfileSignal(
                InteractionKind.LIKE,
                Fixtures.NOW.minus(Duration.ofDays(60)),
                null,
                Fixtures.axis(2)));
    UserProfile p = profiles.build(signals, Fixtures.settings(), Fixtures.NOW).orElseThrow();
    assertThat(p.vector().cosine(Fixtures.axis(1)))
        .isGreaterThan(p.vector().cosine(Fixtures.axis(2)));
    assertThat(p.interactions()).isEqualTo(2);
  }

  @Test
  @DisplayName("a skip pushes the profile away from what was skipped")
  void skipIsNegative() {
    List<ProfileSignal> signals =
        List.of(
            new ProfileSignal(InteractionKind.LIKE, Fixtures.NOW, null, Fixtures.axis(1, 2, 0.5f)),
            new ProfileSignal(InteractionKind.SKIP, Fixtures.NOW, null, Fixtures.axis(2)));
    UserProfile p = profiles.build(signals, Fixtures.settings(), Fixtures.NOW).orElseThrow();
    assertThat(p.vector().cosine(Fixtures.axis(2))).isLessThan(0);
  }

  @Test
  @DisplayName("a view barely watched counts less than a view watched to the end")
  void watchShare() {
    List<ProfileSignal> signals =
        List.of(
            new ProfileSignal(InteractionKind.VIEW, Fixtures.NOW, 100, Fixtures.axis(1)),
            new ProfileSignal(InteractionKind.VIEW, Fixtures.NOW, 10, Fixtures.axis(2)));
    UserProfile p = profiles.build(signals, Fixtures.settings(), Fixtures.NOW).orElseThrow();
    assertThat(p.vector().cosine(Fixtures.axis(1)))
        .isGreaterThan(p.vector().cosine(Fixtures.axis(2)));
  }

  @Test
  @DisplayName("only negative signals make no profile")
  void noPositiveNoProfile() {
    Optional<UserProfile> p =
        profiles.build(
            List.of(
                new ProfileSignal(InteractionKind.REPORT, Fixtures.NOW, null, Fixtures.axis(1))),
            Fixtures.settings(),
            Fixtures.NOW);
    assertThat(p).isEmpty();
  }
}
