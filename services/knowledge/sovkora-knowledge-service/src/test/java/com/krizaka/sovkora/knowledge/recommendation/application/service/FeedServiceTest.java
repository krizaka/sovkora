package com.krizaka.sovkora.knowledge.recommendation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.knowledge.recommendation.domain.model.Feed;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.Fixtures;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.RankedItem;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.RecommendationsDisabledException;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.Strategy;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.UnknownItemException;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.UserProfile;
import com.krizaka.sovkora.knowledge.recommendation.domain.port.RecommendationRepository;
import com.krizaka.sovkora.knowledge.recommendation.domain.port.RecommendationRepository.ItemFacts;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedServiceTest {

  private static final String FAN = "fan0000000000000000000000000001";
  private final RecommendationRepository repository = mock(RecommendationRepository.class);
  private final RecommendationSettingsService settings = mock(RecommendationSettingsService.class);
  private final FeedService feeds =
      new FeedService(
          repository, settings, new RankingService(), Clock.fixed(Fixtures.NOW, ZoneOffset.UTC));

  private static final List<ItemFacts> CATALOGUE =
      List.of(
          new ItemFacts("jazz", "c1", Fixtures.NOW, Fixtures.axis(1)),
          new ItemFacts("cook", "c2", Fixtures.NOW, Fixtures.axis(2)),
          new ItemFacts("seen", "c3", Fixtures.NOW, Fixtures.axis(1)),
          new ItemFacts("bad", "c4", Fixtures.NOW, Fixtures.axis(1)));

  @BeforeEach
  void catalogue() {
    when(settings.forTenant("t")).thenReturn(Fixtures.settings());
    when(repository.freshest(eq("t"), anyInt())).thenReturn(List.of("jazz", "cook", "seen", "bad"));
    when(repository.recentEngagement(eq("t"), any(), anyList(), anyInt())).thenReturn(Map.of());
    when(repository.coEngaged(eq("t"), eq(FAN), anyList(), anyInt())).thenReturn(Map.of());
    when(repository.itemFacts(eq("t"), anyCollection()))
        .thenAnswer(
            inv -> {
              Collection<?> ids = inv.getArgument(1);
              return CATALOGUE.stream().filter(f -> ids.contains(f.itemId())).toList();
            });
  }

  @Test
  @DisplayName("no signal yet: a cold-start feed of fresh items")
  void coldStart() {
    Feed feed = feeds.forYou("t", FAN, 10, Set.of());
    assertThat(feed.strategy()).isEqualTo(Strategy.COLD_START);
    assertThat(feed.items()).hasSize(4);
  }

  @Test
  @DisplayName("with a profile: the nearest item first; seen items and reported creators left out")
  void personal() {
    when(repository.profile("t", FAN))
        .thenReturn(Optional.of(new UserProfile(Fixtures.axis(1), 3)));
    when(repository.nearest(eq("t"), any(), anyInt()))
        .thenReturn(Map.of("jazz", 1.0, "seen", 1.0, "bad", 1.0, "cook", 0.0));
    when(repository.engagedItems("t", FAN)).thenReturn(Set.of("seen"));
    when(repository.reportedCreators("t", FAN)).thenReturn(Set.of("c4"));

    Feed feed = feeds.forYou("t", FAN, 10, Set.of());

    assertThat(feed.strategy()).isEqualTo(Strategy.PERSONAL);
    assertThat(feed.items()).extracting(RankedItem::itemId).containsExactly("jazz", "cook");
  }

  @Test
  @DisplayName("the product's own exclusions are honoured")
  void productExclusions() {
    Feed feed = feeds.forYou("t", FAN, 10, Set.of("jazz"));
    assertThat(feed.items()).extracting(RankedItem::itemId).doesNotContain("jazz");
  }

  @Test
  @DisplayName("switched off: the product is told to fall back")
  void disabled() {
    when(settings.forTenant("t")).thenReturn(Fixtures.settings(false, 2, 0.7));
    assertThatThrownBy(() -> feeds.forYou("t", FAN, 10, Set.of()))
        .isInstanceOf(RecommendationsDisabledException.class);
  }

  @Test
  @DisplayName("similar: never the item itself; an unknown item is a 404")
  void similar() {
    when(repository.item("t", "jazz")).thenReturn(Optional.of(CATALOGUE.getFirst()));
    when(repository.nearest(eq("t"), any(), anyInt()))
        .thenReturn(Map.of("jazz", 1.0, "seen", 1.0, "cook", 0.1));
    Feed feed = feeds.similar("t", "jazz", 5, Set.of());
    assertThat(feed.items()).extracting(RankedItem::itemId).containsExactly("seen", "cook");
    when(repository.item("t", "nope")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> feeds.similar("t", "nope", 5, Set.of()))
        .isInstanceOf(UnknownItemException.class);
  }
}
