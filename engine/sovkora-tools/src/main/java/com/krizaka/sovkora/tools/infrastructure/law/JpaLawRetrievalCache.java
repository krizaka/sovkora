package com.krizaka.sovkora.tools.infrastructure.law;

import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.tools.application.port.law.LawRetrievalCache;
import com.krizaka.sovkora.tools.domain.model.law.CitedArticle;
import com.krizaka.sovkora.tools.domain.model.law.StoredRetrieval;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawRetrievalEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawRetrievalId;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.LawRetrievalRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link LawRetrievalCache} on the job-service's {@code law_retrieval} table, through Spring Data
 * JPA on the engine's own data source (ADR-083). The articles and the claims are JSONB.
 */
@Component
class JpaLawRetrievalCache implements LawRetrievalCache {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final LawRetrievalRepository retrievals;

  JpaLawRetrievalCache(LawRetrievalRepository retrievals) {
    this.retrievals = Objects.requireNonNull(retrievals, "LawRetrievalRepository");
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<StoredRetrieval> find(
      String jurisdiction, String kindId, String language, LocalDate asOfBucket) {
    return retrievals
        .findById(new LawRetrievalId(jurisdiction, kindId, language, asOfBucket))
        .map(JpaLawRetrievalCache::toDomain);
  }

  @Override
  @Transactional
  public void save(StoredRetrieval retrieval) {
    retrievals.upsert(
        retrieval.jurisdiction(),
        retrieval.kindId(),
        retrieval.language(),
        retrieval.asOfBucket(),
        write(retrieval.articles()),
        write(retrieval.claims()),
        retrieval.runId(),
        retrieval.createdAt());
  }

  private static StoredRetrieval toDomain(LawRetrievalEntity row) {
    List<CitedArticle> articles = read(row.getArticles(), new TypeReference<>() {});
    List<Claim> claims = read(row.getClaims(), new TypeReference<>() {});
    return new StoredRetrieval(
        row.getId().getJurisdiction(),
        row.getId().getKindId(),
        row.getId().getLanguage(),
        row.getId().getAsOfBucket(),
        articles,
        claims,
        row.getRunId(),
        row.getCreatedAt(),
        row.isStale());
  }

  private static String write(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalStateException("cannot store the retrieval", e);
    }
  }

  private static <T> T read(String json, TypeReference<T> type) {
    try {
      return JSON.readValue(json, type);
    } catch (JacksonException e) {
      throw new IllegalStateException("cannot read the stored retrieval", e);
    }
  }
}
