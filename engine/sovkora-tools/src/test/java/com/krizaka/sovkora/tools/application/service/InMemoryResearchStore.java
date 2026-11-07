package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.domain.model.research.FetchAudit;
import com.krizaka.sovkora.tools.domain.model.research.FetchedDocument;
import com.krizaka.sovkora.tools.domain.model.research.ResearchSource;
import com.krizaka.sovkora.tools.domain.port.ResearchStore;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Research sources and documents in memory, with the semantics of the store port. */
final class InMemoryResearchStore implements ResearchStore {

  final Map<String, ResearchSource> sources = new HashMap<>();
  final Map<String, FetchedDocument> documents = new HashMap<>();

  /** Declares a source the way the job-service seeds it. */
  ResearchSource declare(String key, String provider, boolean enabled) {
    ResearchSource source =
        new ResearchSource(
            UUID.randomUUID(),
            key,
            "OFFICIAL",
            provider,
            List.of(),
            List.of(),
            false,
            604800,
            10,
            enabled);
    sources.put(key, source);
    return source;
  }

  @Override
  public Optional<ResearchSource> source(String sourceKey) {
    return Optional.ofNullable(sources.get(sourceKey));
  }

  @Override
  public Optional<FetchedDocument> freshDocument(UUID sourceId, String url, Instant notBefore) {
    return Optional.empty();
  }

  @Override
  public Optional<FetchedDocument> document(UUID sourceId, String url, String contentHash) {
    return Optional.ofNullable(documents.get(sourceId + "|" + url + "#" + contentHash));
  }

  @Override
  public void saveDocument(FetchedDocument document, String requestedUrl, UUID sourceId) {
    documents.put(sourceId + "|" + document.url() + "#" + document.contentHash(), document);
  }

  @Override
  public void audit(FetchAudit audit) {}
}
