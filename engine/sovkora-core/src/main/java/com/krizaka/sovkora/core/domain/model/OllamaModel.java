package com.krizaka.sovkora.core.domain.model;

import java.util.Objects;

/**
 * Immutable domain record representing an Ollama model tag.
 *
 * @param name The display name of the model.
 * @param model The model identifier.
 * @param digest The content digest of the model.
 */
public record OllamaModel(String name, String model, String digest) {
  public OllamaModel {
    Objects.requireNonNull(name, "name cannot be null");
    Objects.requireNonNull(model, "model cannot be null");
    Objects.requireNonNull(digest, "digest cannot be null");
  }
}
