package com.krizaka.sovkora.adapter.springai.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import com.krizaka.sovkora.core.domain.ports.outbound.FeatureModelRoutes;
import com.krizaka.sovkora.persistence.domain.model.CatalogModelDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The endpoint of a catalogue model carries its provider's base URL and API key (ADR-030). */
class CatalogModelEndpointResolverTest {

  private static CatalogModelDto model(String name, String provider) {
    CatalogModelDto dto = mock(CatalogModelDto.class);
    when(dto.modelName()).thenReturn(name);
    when(dto.providerName()).thenReturn(provider);
    return dto;
  }

  @Test
  void hostedProvider_endpointCarriesItsKey() {
    CatalogModelManager catalog = mock(CatalogModelManager.class);
    CatalogModelDto mistral = model("mistral-3-14B", "do-inference");
    when(catalog.getDefaultModelByCategory("chat")).thenReturn(Optional.of(mistral));
    when(catalog.getProviderBaseUrl("do-inference")).thenReturn("https://inference.do-ai.run");
    when(catalog.getProviderApiKey("do-inference")).thenReturn("sk-test");

    ModelEndpoint endpoint =
        new CatalogModelEndpointResolver(catalog, feature -> List.of())
            .resolveDefault("chat")
            .orElseThrow();

    assertEquals("https://inference.do-ai.run", endpoint.baseUrl());
    assertEquals("sk-test", endpoint.apiKey());
    assertEquals("mistral-3-14B", endpoint.modelName());
  }

  @Test
  void localProvider_keepsTheKeylessDefault() {
    CatalogModelManager catalog = mock(CatalogModelManager.class);
    CatalogModelDto qwen = model("qwen2.5:7b", "ollama");
    when(catalog.getAllModels()).thenReturn(List.of(qwen));
    when(catalog.getProviderBaseUrl("ollama")).thenReturn("http://localhost:11434");

    ModelEndpoint endpoint =
        new CatalogModelEndpointResolver(catalog, feature -> List.of())
            .resolve("qwen2.5:7b")
            .orElseThrow();

    assertEquals("not-required", endpoint.apiKey()); // ModelEndpoint's value for a keyless provider
  }

  private static final String FEATURE = "agent.code.issue-to-pr";

  private CatalogModelManager catalogWithCodeModels() {
    CatalogModelManager catalog = mock(CatalogModelManager.class);
    CatalogModelDto local = model("qwen2.5-coder:7b", "ollama");
    CatalogModelDto hosted = model("hosted-coder", "do-inference");
    when(catalog.getAllModels()).thenReturn(List.of(local, hosted));
    when(catalog.getDefaultModelByCategory("code")).thenReturn(Optional.of(local));
    when(catalog.getProviderBaseUrl("ollama")).thenReturn("http://localhost:11434");
    when(catalog.getProviderBaseUrl("do-inference")).thenReturn("https://inference.do-ai.run");
    return catalog;
  }

  @Test
  void aFeatureRoutesToItsBestRankedModel() {
    FeatureModelRoutes routes =
        feature ->
            FEATURE.equals(feature) ? List.of("hosted-coder", "qwen2.5-coder:7b") : List.of();

    ModelEndpoint endpoint =
        new CatalogModelEndpointResolver(catalogWithCodeModels(), routes)
            .resolveForFeature(FEATURE, "code")
            .orElseThrow();

    assertEquals("hosted-coder", endpoint.modelName());
  }

  @Test
  void aFeatureWithNoRowFallsBackToTheCategoryDefault() {
    ModelEndpoint endpoint =
        new CatalogModelEndpointResolver(catalogWithCodeModels(), feature -> List.of())
            .resolveForFeature(FEATURE, "code")
            .orElseThrow();

    assertEquals("qwen2.5-coder:7b", endpoint.modelName());
  }

  @Test
  void aRowWhoseModelCannotBeReachedFallsToTheNextRankThenToTheDefault() {
    CatalogModelManager catalog = catalogWithCodeModels();
    when(catalog.getProviderBaseUrl("do-inference")).thenReturn(null);

    ModelEndpoint next =
        new CatalogModelEndpointResolver(
                catalog, feature -> List.of("hosted-coder", "qwen2.5-coder:7b"))
            .resolveForFeature(FEATURE, "code")
            .orElseThrow();
    ModelEndpoint fallback =
        new CatalogModelEndpointResolver(
                catalog, feature -> List.of("hosted-coder", "unknown-model"))
            .resolveForFeature(FEATURE, "code")
            .orElseThrow();

    assertEquals("qwen2.5-coder:7b", next.modelName());
    assertEquals("qwen2.5-coder:7b", fallback.modelName());
  }

  @Test
  void aBlankFeatureKeyIsTheCategoryDefault() {
    ModelEndpoint endpoint =
        new CatalogModelEndpointResolver(
                catalogWithCodeModels(), feature -> List.of("hosted-coder"))
            .resolveForFeature(" ", "code")
            .orElseThrow();

    assertEquals("qwen2.5-coder:7b", endpoint.modelName());
  }
}
