package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.OllamaCatalog;
import java.util.Optional;

/** Outbound port interface for querying available model metadata from active providers. */
public interface ModelCatalogProvider {

  /**
   * Retrieves the catalog of models from the active local provider (Ollama).
   *
   * @return An Optional containing {@link OllamaCatalog} if responsive, or empty otherwise.
   */
  Optional<OllamaCatalog> getCatalog();

  /**
   * Resolves the active/default chat model name.
   *
   * @return An Optional containing the resolved model name, or empty if none could be resolved.
   */
  Optional<String> getActiveChatModel();
}
