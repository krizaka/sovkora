package com.krizaka.sovkora.knowledge.recommendation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.knowledge.recommendation.domain.model.CatalogueItem;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.CatalogueResult;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.Fixtures;
import com.krizaka.sovkora.knowledge.recommendation.domain.port.RecommendationRepository;
import com.krizaka.sovkora.knowledge.recommendation.domain.port.TextEmbeddingClient;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogueServiceTest {

  private final RecommendationRepository repository = mock(RecommendationRepository.class);
  private final TextEmbeddingClient embeddings = mock(TextEmbeddingClient.class);
  private final CatalogueService catalogue =
      new CatalogueService(repository, embeddings, Clock.fixed(Fixtures.NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("only the items whose text changed are embedded; the others keep their vector")
  void embedsOnlyChanged() {
    CatalogueItem same =
        new CatalogueItem("v1", "c1", "same", "", List.of(), null, null, Fixtures.NOW);
    CatalogueItem changed =
        new CatalogueItem("v2", "c1", "new text", "", List.of(), null, null, Fixtures.NOW);
    when(repository.contentHashes(eq("t"), anyCollection()))
        .thenReturn(Map.of("v1", same.contentHash(), "v2", "stale"));
    when(embeddings.embed(List.of(changed.document())))
        .thenReturn(new TextEmbeddingClient.Embeddings("run-1", "gte", List.of(Fixtures.axis(1))));

    CatalogueResult result = catalogue.upsert("t", List.of(same, changed));

    assertThat(result.embedded()).isEqualTo(1);
    assertThat(result.runId()).isEqualTo("run-1");
    verify(repository).upsertItem(eq("t"), eq(same), eq("gte"), isNull(), any());
    verify(repository).upsertItem(eq("t"), eq(changed), eq("gte"), eq(Fixtures.axis(1)), any());
  }
}
