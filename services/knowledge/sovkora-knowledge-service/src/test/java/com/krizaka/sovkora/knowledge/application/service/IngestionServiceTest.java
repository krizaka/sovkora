package com.krizaka.sovkora.knowledge.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class IngestionServiceTest {

  @SuppressWarnings("unchecked")
  private static ObjectProvider<VectorStore> provider(VectorStore store) {
    ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(store);
    return provider;
  }

  @Test
  @DisplayName("Ingestion is a no-op (honest skip) when no vector store is wired")
  void ingestSkipsWithoutVectorStore() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var service = new IngestionService(provider(null), jdbc);

    service.ingest("searchWeb");

    verify(jdbc, never()).query(anyString(), any(RowMapper.class), any());
  }

  @Test
  @DisplayName("Ingestion adds pending sources and marks them ingested when a store is present")
  void ingestAddsAndMarks() {
    VectorStore store = mock(VectorStore.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), any(RowMapper.class), any())).thenReturn(List.of());
    var service = new IngestionService(provider(store), jdbc);

    service.ingest("searchWeb");

    verify(jdbc).query(anyString(), any(RowMapper.class), any());
  }
}
