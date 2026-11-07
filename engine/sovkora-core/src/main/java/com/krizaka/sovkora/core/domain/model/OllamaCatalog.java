package com.krizaka.sovkora.core.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable domain record representing the catalog of local Ollama models.
 *
 * @param models List of discovered models.
 */
public record OllamaCatalog(List<OllamaModel> models) {
  public OllamaCatalog {
    Objects.requireNonNull(models, "models list cannot be null");
    models = List.copyOf(models);
  }
}
