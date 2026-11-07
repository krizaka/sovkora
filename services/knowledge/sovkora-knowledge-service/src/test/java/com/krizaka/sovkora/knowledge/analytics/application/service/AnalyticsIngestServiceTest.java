package com.krizaka.sovkora.knowledge.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.knowledge.analytics.domain.model.AnalyticsEvent;
import com.krizaka.sovkora.knowledge.analytics.domain.model.EventKind;
import com.krizaka.sovkora.knowledge.analytics.domain.model.IngestResult;
import com.krizaka.sovkora.knowledge.analytics.domain.model.KindSource;
import com.krizaka.sovkora.knowledge.analytics.domain.model.ValueUnit;
import com.krizaka.sovkora.knowledge.analytics.domain.port.AnalyticsRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalyticsIngestServiceTest {

  private static final String SUBJECT = "a3f1c9e2b7d84f60a3f1c9e2b7d84f60";
  private static final Instant AT = Instant.parse("2026-10-06T10:00:00Z");

  private final AnalyticsRepository repository = mock(AnalyticsRepository.class);
  private final AnalyticsSettingsService settings = mock(AnalyticsSettingsService.class);
  private final AnalyticsIngestService service = new AnalyticsIngestService(repository, settings);

  {
    when(settings.kinds("t1"))
        .thenReturn(
            Map.of(
                "talk", new EventKind("talk", ValueUnit.SECONDS, KindSource.ANALYTICS),
                "view", new EventKind("view", ValueUnit.PERCENT, KindSource.RECOMMENDER)));
  }

  private static AnalyticsEvent event(String id, String kind) {
    return new AnalyticsEvent(id, SUBJECT, kind, null, null, 60.0, AT);
  }

  @Test
  @DisplayName("events of a known analytics kind are stored; a resent id counts as a duplicate")
  void stores() {
    when(repository.insertEvents(eq("t1"), anyList())).thenReturn(1);
    IngestResult result = service.record("t1", List.of(event("e1", "talk"), event("e2", "talk")));
    assertThat(result).isEqualTo(new IngestResult(1, 1));
  }

  @Test
  @DisplayName(
      "a recommender kind is refused whole, naming the endpoint to use — never counted twice")
  void refusesRecommenderKinds() {
    assertThatThrownBy(
            () -> service.record("t1", List.of(event("e1", "talk"), event("e2", "view"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("/api/v1/recommendations/events");
    verify(repository, never()).insertEvents(eq("t1"), anyList());
  }

  @Test
  @DisplayName("an unknown kind is refused: kinds are rows, not whatever a client invents")
  void refusesUnknownKinds() {
    assertThatThrownBy(() -> service.record("t1", List.of(event("e1", "teleport"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown kind: teleport");
  }
}
