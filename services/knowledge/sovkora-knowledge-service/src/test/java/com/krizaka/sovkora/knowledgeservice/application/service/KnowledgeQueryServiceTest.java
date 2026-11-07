package com.krizaka.sovkora.knowledgeservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.knowledgeservice.domain.model.ActingPrincipal;
import com.krizaka.sovkora.knowledgeservice.domain.port.AccessAudit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class KnowledgeQueryServiceTest {

  private static final ActingPrincipal EMPLOYEE =
      new ActingPrincipal("acme", "emp-1", Set.of("ROLE_USER"), Set.of());

  private final AccessAudit audit = mock(AccessAudit.class);

  @SuppressWarnings("unchecked")
  private static ObjectProvider<VectorStore> provider(VectorStore store) {
    ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(store);
    return provider;
  }

  @Test
  @DisplayName("retrieveContext returns empty when no vector store is wired (dormant)")
  void retrieveDegradesWithoutVectorStore() {
    var service = new KnowledgeQueryService(provider(null), mock(JdbcTemplate.class), audit);
    assertThat(service.retrieveContext(EMPLOYEE, "anything", 3)).isEmpty();
  }

  @Test
  @DisplayName("retrieveContext joins the text and asks the store for the readable passages only")
  void retrieveFiltersInsideTheSearch() {
    VectorStore store = mock(VectorStore.class);
    when(store.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(new Document("alpha"), new Document("beta")));
    var service = new KnowledgeQueryService(provider(store), mock(JdbcTemplate.class), audit);

    assertThat(service.retrieveContext(EMPLOYEE, "q", 2)).isEqualTo("alpha\n---\nbeta");

    ArgumentCaptor<SearchRequest> requests = ArgumentCaptor.forClass(SearchRequest.class);
    verify(store, org.mockito.Mockito.atLeastOnce()).similaritySearch(requests.capture());
    SearchRequest main = requests.getAllValues().getFirst();
    assertThat(main.getFilterExpression()).isNotNull();
    assertThat(main.getFilterExpression().toString()).contains("tenant_id").contains("acme");
  }

  @Test
  @DisplayName("searchSources joins matching rows and is empty for a blank tool id")
  @SuppressWarnings("unchecked")
  void searchSourcesJoinsRows() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of("one", "two"))
        .thenReturn(List.of());
    var service = new KnowledgeQueryService(provider(null), jdbc, audit);

    assertThat(service.searchSources(EMPLOYEE, "searchKnowledge", "q")).isEqualTo("one\n\ntwo");
    assertThat(service.searchSources(EMPLOYEE, "  ", "q")).isEmpty();
  }

  @Test
  @DisplayName("a read is never made without a principal")
  void aReadNeedsAPrincipal() {
    var service = new KnowledgeQueryService(provider(null), mock(JdbcTemplate.class), audit);
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class, () -> service.searchSources(null, "searchKnowledge", "q"));
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class, () -> service.retrieveContext(null, "q", 3));
  }
}
