package com.krizaka.sovkora.knowledge.recommendation.domain.port;

import com.krizaka.sovkora.knowledge.recommendation.domain.model.Vector;
import java.util.List;

/**
 * The platform's embedding model, as the recommender sees it: texts in, vectors out, on behalf of
 * the caller (the tenant's own session, so the call is the tenant's — never a model named here).
 */
public interface TextEmbeddingClient {

  /**
   * Embeds the texts, in order.
   *
   * @throws
   *     com.krizaka.sovkora.knowledge.recommendation.domain.model.EmbeddingUnavailableException
   *     when the model is out of reach or answers another shape
   */
  Embeddings embed(List<String> texts);

  /**
   * The vectors and the model that made them.
   *
   * @param runId the embedding run ({@code embedding.run runId=…})
   * @param model the model
   * @param vectors one per text, in order
   */
  record Embeddings(String runId, String model, List<Vector> vectors) {}
}
