package com.krizaka.sovkora.knowledgeservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.knowledgeservice.domain.model.Acl;
import com.krizaka.sovkora.knowledgeservice.domain.model.ActingPrincipal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/**
 * The access rule as the vector store applies it. Every passage is about the same words, so the
 * similarity ranking alone could not tell them apart: only the filter decides what comes back.
 */
class VectorAccessTest {

  /** Two texts with the same words embed identically, whatever their access rule. */
  private static final EmbeddingModel BAG_OF_LETTERS =
      new EmbeddingModel() {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
          throw new UnsupportedOperationException();
        }

        @Override
        public float[] embed(Document document) {
          return embed(document.getText());
        }

        @Override
        public float[] embed(String text) {
          float[] vector = new float[26];
          for (char c : text.toLowerCase().toCharArray()) {
            if (c >= 'a' && c <= 'z') {
              vector[c - 'a']++;
            }
          }
          return vector;
        }
      };

  private SimpleVectorStore store;

  private void add(long id, String tenant, String owner, String... acl) {
    store.add(
        List.of(
            new Document(
                "ceo salary " + id,
                Acl.metadata("searchKnowledge", id, tenant, owner, List.of(acl)))));
  }

  @BeforeEach
  void indexed() {
    store = SimpleVectorStore.builder(BAG_OF_LETTERS).build();
    add(1, "acme", "hr-1", "role:ROLE_HR");
    add(2, "acme", null, "role:ROLE_USER");
    add(3, "globex", "hr-9", "role:ROLE_HR");
    add(4, "*", null, "role:ROLE_USER");
    add(5, "acme", "emp-1");
  }

  private Set<String> search(ActingPrincipal who, boolean readable) {
    return store
        .similaritySearch(
            SearchRequest.builder()
                .query("ceo salary")
                .topK(10)
                .similarityThreshold(0.0)
                .filterExpression(
                    readable ? VectorAccess.readable(who) : VectorAccess.unreadable(who))
                .build())
        .stream()
        .map(d -> d.getMetadata().get(Acl.SOURCE).toString())
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName(
      "an employee reads the user-granted, platform and own passages, not HR's nor another tenant's")
  void employeeReadsOnlyWhatIsGranted() {
    var employee = new ActingPrincipal("acme", "emp-1", Set.of("ROLE_USER"), Set.of());

    assertThat(search(employee, true)).containsExactlyInAnyOrder("2", "4", "5");
    assertThat(search(employee, false)).containsExactly("1");
  }

  @Test
  @DisplayName("an HR member also reads the HR passage, still not another tenant's")
  void hrReadsTheHrPassage() {
    var hr = new ActingPrincipal("acme", "hr-2", Set.of("ROLE_USER", "ROLE_HR"), Set.of());

    assertThat(search(hr, true)).containsExactlyInAnyOrder("1", "2", "4");
  }

  @Test
  @DisplayName("the same role in another tenant reads that tenant's passage only")
  void tenantsAreIsolated() {
    var globexHr = new ActingPrincipal("globex", "hr-8", Set.of("ROLE_HR"), Set.of());

    assertThat(search(globexHr, true)).containsExactlyInAnyOrder("3");
  }

  @Test
  @DisplayName("a group grant works like a role grant, and two entries never share a key")
  void groupsGrant() {
    add(6, "acme", null, "group:GROUP_FINANCE");
    var finance = new ActingPrincipal("acme", "fin-1", Set.of(), Set.of("GROUP_FINANCE"));
    var lookalike = new ActingPrincipal("acme", "fin-2", Set.of("GROUP_FINANCE"), Set.of());

    assertThat(search(finance, true)).contains("6");
    assertThat(search(lookalike, true)).doesNotContain("6");
    assertThat(Acl.key("role:A_B")).isNotEqualTo(Acl.key("role_A:B"));
  }
}
