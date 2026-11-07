package com.krizaka.sovkora.adapter.springai.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.model.OllamaCatalog;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class OllamaModelCatalogProviderTest {

  private RestClient restClient;
  private RestClient.Builder restClientBuilder;

  @BeforeEach
  void setUp() {
    restClientBuilder = mock(RestClient.Builder.class);
    restClient = mock(RestClient.class);
    when(restClientBuilder.baseUrl(any(String.class))).thenReturn(restClientBuilder);
    when(restClientBuilder.build()).thenReturn(restClient);
  }

  private OllamaModelCatalogProvider createProvider(String activeModel) {
    return new OllamaModelCatalogProvider(restClientBuilder, "http://localhost:11434", activeModel);
  }

  @Test
  @SuppressWarnings("unchecked")
  void getCatalog_returnsModelsFromOllama() {
    var provider = createProvider("");
    RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.get()).thenReturn(uriSpec);
    when(uriSpec.uri("/api/tags")).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);

    Map<String, Object> body =
        Map.of(
            "models",
            List.of(
                Map.of("name", "llama3:latest", "model", "llama3", "digest", "abc123"),
                Map.of("name", "codellama:7b", "model", "codellama", "digest", "def456")));
    when(responseSpec.body(any(Class.class))).thenReturn(body);

    Optional<OllamaCatalog> catalog = provider.getCatalog();

    assertThat(catalog).isPresent();
    assertThat(catalog.get().models()).hasSize(2);
    assertThat(catalog.get().models().getFirst().name()).isEqualTo("llama3:latest");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getCatalog_returnsEmptyWhenBodyIsNull() {
    var provider = createProvider("");
    RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.get()).thenReturn(uriSpec);
    when(uriSpec.uri("/api/tags")).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(any(Class.class))).thenReturn(null);

    Optional<OllamaCatalog> catalog = provider.getCatalog();
    assertThat(catalog).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void getCatalog_returnsEmptyCatalogWhenModelsListIsNull() {
    var provider = createProvider("");
    RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.get()).thenReturn(uriSpec);
    when(uriSpec.uri("/api/tags")).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(any(Class.class))).thenReturn(Map.of());

    Optional<OllamaCatalog> catalog = provider.getCatalog();
    assertThat(catalog).isPresent();
    assertThat(catalog.get().models()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void getCatalog_returnsEmptyOnRestClientException() {
    var provider = createProvider("");
    RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.get()).thenReturn(uriSpec);
    when(uriSpec.uri("/api/tags")).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(any(Class.class)))
        .thenThrow(new RestClientException("Connection refused"));

    Optional<OllamaCatalog> catalog = provider.getCatalog();
    assertThat(catalog).isEmpty();
  }

  @Test
  void getActiveChatModel_returnsConfiguredModel() {
    var provider = createProvider("llama3");
    Optional<String> model = provider.getActiveChatModel();
    assertThat(model).contains("llama3");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getActiveChatModel_fallsBackToCatalogWhenBlank() {
    var provider = createProvider("");
    RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.get()).thenReturn(uriSpec);
    when(uriSpec.uri("/api/tags")).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(any(Class.class)))
        .thenReturn(
            Map.of(
                "models",
                List.of(
                    Map.of("name", "nomic-embed-text", "model", "nomic-embed-text", "digest", "x"),
                    Map.of("name", "llama3:latest", "model", "llama3", "digest", "y"))));

    Optional<String> model = provider.getActiveChatModel();
    assertThat(model).contains("llama3:latest");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getActiveChatModel_returnsEmbedModelIfOnlyOptionAvailable() {
    var provider = createProvider("");
    RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.get()).thenReturn(uriSpec);
    when(uriSpec.uri("/api/tags")).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(any(Class.class)))
        .thenReturn(
            Map.of(
                "models",
                List.of(
                    Map.of(
                        "name", "nomic-embed-text", "model", "nomic-embed-text", "digest", "x"))));

    Optional<String> model = provider.getActiveChatModel();
    assertThat(model).contains("nomic-embed-text");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getCatalog_skipsModelsWithNullName() {
    var provider = createProvider("");
    RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.get()).thenReturn(uriSpec);
    when(uriSpec.uri("/api/tags")).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(any(Class.class)))
        .thenReturn(
            Map.of(
                "models",
                List.of(
                    Map.of("model", "orphan-no-name"), Map.of("name", "valid", "model", "valid"))));

    Optional<OllamaCatalog> catalog = provider.getCatalog();
    assertThat(catalog).isPresent();
    assertThat(catalog.get().models()).hasSize(1);
  }

  @Test
  void getActiveChatModel_catalogueDefaultBeatsOllamaListOrder() {
    var provider =
        new OllamaModelCatalogProvider(
            restClientBuilder, "http://localhost:11434", "", () -> Optional.of("mistral-3-14B"));
    // Ollama is never asked: the catalogue default answers first (restClient is an unstubbed mock)
    assertThat(provider.getActiveChatModel()).contains("mistral-3-14B");
  }

  @Test
  void getActiveChatModel_explicitConfigBeatsCatalogueDefault() {
    var provider =
        new OllamaModelCatalogProvider(
            restClientBuilder,
            "http://localhost:11434",
            "llama3",
            () -> Optional.of("mistral-3-14B"));
    assertThat(provider.getActiveChatModel()).contains("llama3");
  }
}
