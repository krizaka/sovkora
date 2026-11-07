package com.krizaka.sovkora.interceptor.cache;

import com.krizaka.sovkora.core.domain.ports.outbound.EmbeddingClient;
import java.util.List;
import java.util.Objects;

/**
 * The cache's {@link TextEmbedder}, served by the engine's {@link EmbeddingClient}: the model is
 * the catalogue's default embedding model (data in {@code orazaka_models}), never named here.
 */
public final class EmbeddingClientTextEmbedder implements TextEmbedder {

  private final EmbeddingClient client;

  public EmbeddingClientTextEmbedder(EmbeddingClient client) {
    this.client = Objects.requireNonNull(client, "EmbeddingClient cannot be null");
  }

  @Override
  public float[] embed(String text) {
    return client.embed(List.of(text), null).vectors().getFirst();
  }
}
