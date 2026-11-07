package com.krizaka.sovkora.knowledgeservice.recommendation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Candidate;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Fixtures;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.RankedItem;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Reason;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingServiceTest {

  private final RankingService ranking = new RankingService();

  private static Candidate candidate(
      String id,
      String creator,
      int dim,
      double sim,
      double co,
      double pop,
      boolean followed,
      Set<Reason> sources) {
    return new Candidate(
        id,
        creator,
        Fixtures.NOW.minus(Duration.ofDays(1)),
        Fixtures.axis(dim),
        sim,
        co,
        pop,
        followed,
        sources);
  }

  @Test
  @DisplayName("the item nearest to the profile ranks first, and says why")
  void similarityWins() {
    List<RankedItem> ranked =
        ranking.rank(
            List.of(
                candidate("far", "c1", 1, 0.1, 0, 0, false, Set.of(Reason.SIMILAR)),
                candidate("near", "c2", 2, 0.9, 0, 0, false, Set.of(Reason.SIMILAR))),
            Fixtures.settings(),
            2,
            Fixtures.NOW);
    assertThat(ranked).extracting(RankedItem::itemId).containsExactly("near", "far");
    assertThat(ranked.getFirst().reasons()).contains(Reason.SIMILAR);
  }

  @Test
  @DisplayName("a reason is only given for a source that actually proposed the item")
  void reasonsAreSources() {
    List<RankedItem> ranked =
        ranking.rank(
            List.of(candidate("a", "c1", 1, 0.9, 0, 0, false, Set.of(Reason.FRESH))),
            Fixtures.settings(),
            1,
            Fixtures.NOW);
    assertThat(ranked.getFirst().reasons()).containsExactly(Reason.FRESH);
  }

  @Test
  @DisplayName("the creator cap holds: never more than N items of one creator on a page")
  void creatorCap() {
    List<RankedItem> ranked =
        ranking.rank(
            List.of(
                candidate("a1", "a", 1, 0.9, 0, 0, false, Set.of(Reason.SIMILAR)),
                candidate("a2", "a", 2, 0.89, 0, 0, false, Set.of(Reason.SIMILAR)),
                candidate("a3", "a", 3, 0.88, 0, 0, false, Set.of(Reason.SIMILAR)),
                candidate("b1", "b", 4, 0.2, 0, 0, false, Set.of(Reason.SIMILAR))),
            Fixtures.settings(true, 2, 0.7),
            4,
            Fixtures.NOW);
    assertThat(ranked).extracting(RankedItem::itemId).containsExactly("a1", "a2", "b1");
  }

  @Test
  @DisplayName("MMR: a near-duplicate of a picked item gives way to a different one")
  void mmrDiversifies() {
    Candidate first =
        new Candidate(
            "x1", "c1", Fixtures.NOW, Fixtures.axis(1), 0.95, 0, 0, false, Set.of(Reason.SIMILAR));
    Candidate twin =
        new Candidate(
            "x2", "c2", Fixtures.NOW, Fixtures.axis(1), 0.94, 0, 0, false, Set.of(Reason.SIMILAR));
    Candidate other =
        new Candidate(
            "y", "c3", Fixtures.NOW, Fixtures.axis(9), 0.8, 0, 0, false, Set.of(Reason.SIMILAR));
    List<RankedItem> diverse =
        ranking.rank(List.of(first, twin, other), Fixtures.settings(true, 5, 0.5), 2, Fixtures.NOW);
    assertThat(diverse).extracting(RankedItem::itemId).containsExactly("x1", "y");
    List<RankedItem> relevanceOnly =
        ranking.rank(List.of(first, twin, other), Fixtures.settings(true, 5, 1.0), 2, Fixtures.NOW);
    assertThat(relevanceOnly).extracting(RankedItem::itemId).containsExactly("x1", "x2");
  }

  @Test
  @DisplayName("a followed creator's item and a fresh one outrank an old unknown one")
  void followedAndFresh() {
    Candidate old =
        new Candidate(
            "old",
            "c1",
            Fixtures.NOW.minus(Duration.ofDays(60)),
            null,
            0,
            0,
            0,
            false,
            Set.of(Reason.FRESH));
    Candidate followed =
        new Candidate(
            "f",
            "c2",
            Fixtures.NOW.minus(Duration.ofDays(60)),
            null,
            0,
            0,
            0,
            true,
            Set.of(Reason.FOLLOWED));
    Candidate fresh =
        new Candidate("n", "c3", Fixtures.NOW, null, 0, 0, 0, false, Set.of(Reason.FRESH));
    assertThat(ranking.rank(List.of(old, followed, fresh), Fixtures.settings(), 3, Fixtures.NOW))
        .extracting(RankedItem::itemId)
        .containsExactly("n", "f", "old");
  }

  @Test
  @DisplayName("no candidate, no item")
  void empty() {
    assertThat(ranking.rank(List.of(), Fixtures.settings(), 5, Fixtures.NOW)).isEmpty();
  }
}
