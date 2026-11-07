package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.LawRetrievalCache;
import com.krizaka.sovkora.tools.domain.model.law.StoredRetrieval;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** {@code law_retrieval} in memory: one row per (jurisdiction, kind, bucket). */
final class InMemoryLawRetrievals implements LawRetrievalCache {

  final Map<String, StoredRetrieval> rows = new HashMap<>();

  @Override
  public Optional<StoredRetrieval> find(
      String jurisdiction, String kindId, String language, LocalDate asOfBucket) {
    return Optional.ofNullable(rows.get(key(jurisdiction, kindId, language, asOfBucket)));
  }

  @Override
  public void save(StoredRetrieval r) {
    rows.put(key(r.jurisdiction(), r.kindId(), r.language(), r.asOfBucket()), r);
  }

  /** What the corpus store does when a cited article changes. */
  void markStale(String instrument, String ref) {
    rows.replaceAll(
        (k, r) ->
            r.articles().stream()
                    .anyMatch(a -> a.instrument().equals(instrument) && a.ref().equals(ref))
                ? new StoredRetrieval(
                    r.jurisdiction(),
                    r.kindId(),
                    r.language(),
                    r.asOfBucket(),
                    r.articles(),
                    r.claims(),
                    r.runId(),
                    r.createdAt(),
                    true)
                : r);
  }

  private static String key(String jurisdiction, String kindId, String language, LocalDate bucket) {
    return jurisdiction + "|" + kindId + "|" + language + "|" + bucket;
  }
}
