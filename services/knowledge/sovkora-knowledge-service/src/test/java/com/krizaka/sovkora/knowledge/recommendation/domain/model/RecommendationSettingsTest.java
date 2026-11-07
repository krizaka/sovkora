package com.krizaka.sovkora.knowledge.recommendation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendationSettingsTest {

  @Test
  @DisplayName("positive kinds are the item kinds weighted above zero")
  void positiveKinds() {
    assertThat(Fixtures.settings().positiveKinds())
        .containsExactly(
            InteractionKind.VIEW,
            InteractionKind.LIKE,
            InteractionKind.SAVE,
            InteractionKind.SHARE,
            InteractionKind.TIP,
            InteractionKind.UNLOCK);
  }

  @Test
  @DisplayName("a published version outside the safety bounds is refused")
  void bounds() {
    RecommendationSettings s = Fixtures.settings();
    assertThatThrownBy(
            () ->
                new RecommendationSettings(
                    2,
                    true,
                    s.weights(),
                    s.kindWeights(),
                    168,
                    72,
                    1.5,
                    2,
                    100,
                    200,
                    7,
                    true,
                    180,
                    30,
                    3))
        .hasMessageContaining("mmrLambda");
    assertThatThrownBy(
            () ->
                new RecommendationSettings(
                    2,
                    true,
                    s.weights(),
                    Map.of(InteractionKind.LIKE, 99.0),
                    168,
                    72,
                    0.7,
                    2,
                    100,
                    200,
                    7,
                    true,
                    180,
                    30,
                    3))
        .hasMessageContaining("kindWeights.LIKE");
    assertThatThrownBy(() -> new RankingWeights(2, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
