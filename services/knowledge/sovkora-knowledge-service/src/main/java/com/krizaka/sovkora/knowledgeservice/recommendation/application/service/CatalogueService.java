package com.krizaka.sovkora.knowledgeservice.recommendation.application.service;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.CatalogueItem;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.CatalogueResult;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.ErasureResult;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Vector;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.port.RecommendationRepository;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.port.TextEmbeddingClient;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Item understanding, text half (ADR-078 §1): the product's eligible items are upserted, and the
 * ones whose text changed are embedded with the platform's model. The title and the description are
 * not stored — only their hash and their vector.
 */
@Service
public class CatalogueService {

  private static final Logger logger = LoggerFactory.getLogger(CatalogueService.class);

  /** The embedding endpoint takes at most 128 texts; a smaller batch keeps each call short. */
  private static final int EMBED_BATCH = 64;

  private final RecommendationRepository repository;
  private final TextEmbeddingClient embeddings;
  private final Clock clock;

  public CatalogueService(
      RecommendationRepository repository, TextEmbeddingClient embeddings, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.embeddings = Objects.requireNonNull(embeddings, "embeddings");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Upserts the items, embedding the changed ones. */
  public CatalogueResult upsert(String tenant, List<CatalogueItem> items) {
    Instant now = clock.instant();
    Map<String, String> known =
        repository.contentHashes(tenant, items.stream().map(CatalogueItem::itemId).toList());
    List<CatalogueItem> changed =
        items.stream().filter(i -> !i.contentHash().equals(known.get(i.itemId()))).toList();
    Map<String, Vector> vectors = new HashMap<>();
    String model = null;
    String runId = null;
    for (int from = 0; from < changed.size(); from += EMBED_BATCH) {
      List<CatalogueItem> batch =
          changed.subList(from, Math.min(changed.size(), from + EMBED_BATCH));
      TextEmbeddingClient.Embeddings result =
          embeddings.embed(batch.stream().map(CatalogueItem::document).toList());
      for (int i = 0; i < batch.size(); i++) {
        vectors.put(batch.get(i).itemId(), result.vectors().get(i));
      }
      model = result.model();
      runId = result.runId();
    }
    for (CatalogueItem item : items) {
      repository.upsertItem(tenant, item, model, vectors.get(item.itemId()), now);
    }
    logger.info(
        "recommendation.items tenant={} upserted={} embedded={} embeddingRun={}",
        tenant,
        items.size(),
        changed.size(),
        runId);
    return new CatalogueResult(items.size(), changed.size(), model, runId);
  }

  /** Forgets people (account deleted) and items (left the product's discovery). */
  public ErasureResult erase(String tenant, List<String> userIds, List<String> itemIds) {
    int events = userIds.isEmpty() ? 0 : repository.eraseUsers(tenant, userIds);
    int items = itemIds.isEmpty() ? 0 : repository.eraseItems(tenant, itemIds);
    logger.info(
        "recommendation.erasure tenant={} users={} events={} items={}",
        tenant,
        userIds.size(),
        events,
        items);
    return new ErasureResult(events, items);
  }
}
