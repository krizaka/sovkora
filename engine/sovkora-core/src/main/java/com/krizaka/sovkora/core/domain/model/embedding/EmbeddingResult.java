package com.krizaka.sovkora.core.domain.model.embedding;

import java.util.List;
import java.util.Objects;

/**
 * The vectors a model produced for a batch of inputs.
 *
 * @param model the model that embedded them (as resolved from the catalogue)
 * @param vectors one vector per input, in the order of the inputs
 * @param promptTokens tokens billed by the provider for the batch ({@code 0} when it did not say)
 */
public record EmbeddingResult(String model, List<float[]> vectors, int promptTokens) {

  public EmbeddingResult {
    Objects.requireNonNull(model, "model cannot be null");
    vectors = List.copyOf(Objects.requireNonNull(vectors, "vectors cannot be null"));
  }

  /** Dimension of the vectors ({@code 0} for an empty batch). */
  public int dimensions() {
    return vectors.isEmpty() ? 0 : vectors.getFirst().length;
  }
}
