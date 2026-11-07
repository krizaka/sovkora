package com.krizaka.sovkora.adapter.springai.remote;

import com.krizaka.sovkora.core.domain.model.OllamaCatalog;
import com.krizaka.sovkora.core.domain.model.OllamaModel;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Outbound adapter that queries the local Ollama instance for installed models using Spring's
 * native {@link RestClient}.
 */
public class OllamaModelCatalogProvider implements ModelCatalogProvider {

  private static final Logger log = LoggerFactory.getLogger(OllamaModelCatalogProvider.class);
  private final RestClient restClient;
  private final String activeOllamaModel;
  private final Supplier<Optional<String>> catalogueDefaultChatModel;

  public OllamaModelCatalogProvider(
      RestClient.Builder restClientBuilder,
      @Value("${spring.ai.ollama.base-url}") String ollamaBaseUrl,
      @Value("${ORAZAKA_OLLAMA_MODEL:${spring.ai.ollama.chat.options.model:}}")
          String activeOllamaModel) {
    this(restClientBuilder, ollamaBaseUrl, activeOllamaModel, Optional::empty);
  }

  /**
   * @param catalogueDefaultChatModel the chat model the catalogue ({@code
   *     orazaka_models.is_default}) marks as default: it wins over Ollama's list order, and only an
   *     explicit configured model wins over it
   */
  public OllamaModelCatalogProvider(
      RestClient.Builder restClientBuilder,
      String ollamaBaseUrl,
      String activeOllamaModel,
      Supplier<Optional<String>> catalogueDefaultChatModel) {
    Objects.requireNonNull(restClientBuilder, "RestClient.Builder must not be null");
    this.restClient = restClientBuilder.baseUrl(ollamaBaseUrl).build();
    this.activeOllamaModel = activeOllamaModel;
    this.catalogueDefaultChatModel = catalogueDefaultChatModel;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<OllamaCatalog> getCatalog() {
    try {
      Map<String, Object> body = restClient.get().uri("/api/tags").retrieve().body(Map.class);

      if (body == null) {
        return Optional.empty();
      }

      List<Map<String, Object>> modelsList = (List<Map<String, Object>>) body.get("models");
      if (modelsList == null) {
        return Optional.of(new OllamaCatalog(List.of()));
      }

      List<OllamaModel> models = new ArrayList<>();
      for (Map<String, Object> m : modelsList) {
        String name = (String) m.get("name");
        String model = (String) m.get("model");
        String digest = (String) m.get("digest");
        if (name != null && model != null) {
          models.add(new OllamaModel(name, model, digest != null ? digest : ""));
        }
      }
      return Optional.of(new OllamaCatalog(models));
    } catch (RestClientException e) {
      log.error("Failed to query Ollama catalog", e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<String> getActiveChatModel() {
    if (activeOllamaModel != null && !activeOllamaModel.isBlank()) {
      return Optional.of(activeOllamaModel);
    }
    Optional<String> catalogueDefault = catalogueDefaultChatModel.get().filter(m -> !m.isBlank());
    if (catalogueDefault.isPresent()) {
      return catalogueDefault;
    }
    return getCatalog()
        .flatMap(
            catalog ->
                catalog.models().stream()
                    .map(OllamaModel::name)
                    .filter(name -> name != null && !name.toLowerCase().contains("embed"))
                    .findFirst()
                    .or(
                        () ->
                            catalog.models().stream()
                                .map(OllamaModel::name)
                                .filter(Objects::nonNull)
                                .findFirst()));
  }
}
