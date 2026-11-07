package com.krizaka.sovkora.adapter.springai.remote;

import com.krizaka.sovkora.core.domain.model.ClassificationResponse;
import com.krizaka.sovkora.core.domain.ports.outbound.SemanticClassifierPort;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * LocalAI-based implementation of the {@link SemanticClassifierPort}.
 *
 * <p>Sends prompts to a local LocalAI instance (or any compatible endpoint) for semantic
 * classification to drive the two-phase execution pipeline.
 */
public class LocalAiSemanticClassifierAdapter implements SemanticClassifierPort {

  private static final Logger logger =
      LoggerFactory.getLogger(LocalAiSemanticClassifierAdapter.class);

  private final RestClient restClient;
  private final String endpoint;

  public LocalAiSemanticClassifierAdapter(
      RestClient.Builder restClientBuilder, CoreProperties properties) {
    this.restClient = restClientBuilder.build();
    this.endpoint = properties.orchestration().routing().semanticEndpoint();
  }

  @Override
  public ClassificationResponse classify(String prompt) {
    try {
      ClassificationResponse response =
          restClient
              .post()
              .uri(endpoint)
              .body(Map.of("input", prompt))
              .retrieve()
              .body(ClassificationResponse.class);
      if (response == null) {
        logger.warn("LocalAI classification returned null response — degrading to deterministic.");
        return new ClassificationResponse(List.of());
      }
      logger.debug(
          "LocalAiSemanticClassifierAdapter classified prompt with {} intent(s).",
          response.intents().size());
      return response;
    } catch (RestClientException e) {
      logger.warn(
          "LocalAI classification endpoint unreachable — degrading to deterministic pipeline.", e);
      return new ClassificationResponse(List.of());
    }
  }
}
