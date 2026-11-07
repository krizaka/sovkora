package com.krizaka.sovkora.adapter.springai.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.ClassificationResponse;
import com.krizaka.sovkora.core.domain.model.RoutingMode;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class LocalAiSemanticClassifierAdapterTest {

  private RestClient.ResponseSpec responseSpec;
  private LocalAiSemanticClassifierAdapter adapter;

  @BeforeEach
  void setUp() {
    responseSpec = mock(RestClient.ResponseSpec.class);

    RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);

    var routing =
        new CoreProperties.RoutingConfig(RoutingMode.DETERMINISTIC, "http://test:8085/v1/classify");
    var orchestration = new CoreProperties.OrchestrationConfig(true, null, null, routing);
    var properties =
        new CoreProperties("ollama", null, null, orchestration, null, null, null, null);

    when(restClient
            .post()
            .uri(properties.orchestration().routing().semanticEndpoint())
            .body(Map.of("input", "test prompt"))
            .retrieve())
        .thenReturn(responseSpec);

    RestClient.Builder builder = mock(RestClient.Builder.class);
    when(builder.build()).thenReturn(restClient);

    adapter = new LocalAiSemanticClassifierAdapter(builder, properties);
  }

  @Test
  void classify_returnsResponseOnSuccess() {
    var expected = new ClassificationResponse(List.of());
    when(responseSpec.body(ClassificationResponse.class)).thenReturn(expected);

    var result = adapter.classify("test prompt");

    assertThat(result).isSameAs(expected);
  }

  @Test
  void classify_returnsEmptyOnNullResponse() {
    when(responseSpec.body(ClassificationResponse.class)).thenReturn(null);

    var result = adapter.classify("test prompt");

    assertThat(result).isNotNull();
    assertThat(result.intents()).isEmpty();
  }

  @Test
  void classify_degradesGracefullyOnRestClientException() {
    when(responseSpec.body(ClassificationResponse.class))
        .thenThrow(new RestClientException("Connection refused"));

    var result = adapter.classify("test prompt");

    assertThat(result).isNotNull();
    assertThat(result.intents()).isEmpty();
  }
}
