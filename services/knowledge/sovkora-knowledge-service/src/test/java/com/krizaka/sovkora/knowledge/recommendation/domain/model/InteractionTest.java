package com.krizaka.sovkora.knowledge.recommendation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InteractionTest {

  private static final String FAN = "3f1d2a9c8b7e6d5c4b3a2918171615141312";

  @Test
  @DisplayName("a raw e-mail is never accepted as a user: only a pseudonym is (Loi 25)")
  void refusesAnEmailAsUser() {
    assertThatThrownBy(
            () ->
                new Interaction(
                    "e1", "fan@example.com", "v1", null, InteractionKind.LIKE, null, Fixtures.NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pseudonym");
  }

  @Test
  @DisplayName("a FOLLOW targets a creator; any other kind targets an item")
  void targets() {
    Interaction follow =
        new Interaction("e1", FAN, "ignored", "c1", InteractionKind.FOLLOW, null, Fixtures.NOW);
    assertThat(follow.itemId()).isNull();
    assertThatThrownBy(
            () -> new Interaction("e2", FAN, null, "c1", InteractionKind.LIKE, null, Fixtures.NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Interaction("e3", FAN, "v1", null, InteractionKind.FOLLOW, null, Fixtures.NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("watchedPct is a share between 0 and 100")
  void watchedPct() {
    assertThatThrownBy(
            () -> new Interaction("e1", FAN, "v1", null, InteractionKind.VIEW, 101, Fixtures.NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
