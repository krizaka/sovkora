package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.embedding.EmbeddingException;
import com.krizaka.sovkora.core.domain.model.embedding.EmbeddingResult;
import java.util.List;

/**
 * Port: turns texts into vectors with the platform's embedding model — the one an administrator
 * sets in the catalogue ({@code orazaka_models}, category {@code embedding}), never a provider the
 * caller names.
 */
public interface EmbeddingClient {

  /**
   * Embeds a batch of texts.
   *
   * @param inputs the texts, non-empty, none blank
   * @param model a catalogue model name, or {@code null} for the category's default
   * @throws EmbeddingException when no model is configured or the provider fails
   */
  EmbeddingResult embed(List<String> inputs, String model);
}
