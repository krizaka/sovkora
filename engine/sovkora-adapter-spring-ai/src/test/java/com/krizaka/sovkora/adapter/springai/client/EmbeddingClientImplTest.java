package com.krizaka.sovkora.adapter.springai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import com.krizaka.sovkora.core.domain.model.embedding.EmbeddingException;
import com.krizaka.sovkora.core.domain.model.embedding.EmbeddingResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class EmbeddingClientImplTest {

  private HttpServer server;
  private final AtomicReference<String> body = new AtomicReference<>();
  private final AtomicReference<String> auth = new AtomicReference<>();
  private final AtomicReference<String> path = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String answer;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          path.set(exchange.getRequestURI().getPath());
          byte[] out = answer.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, out.length);
          exchange.getResponseBody().write(out);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private EmbeddingClientImpl client(Optional<ModelEndpoint> endpoint) {
    return new EmbeddingClientImpl(new FixedResolver(endpoint), RestClient.builder());
  }

  private ModelEndpoint endpoint() {
    return new ModelEndpoint(
        "do-inference", "http://127.0.0.1:" + server.getAddress().getPort(), "k-123", "bge-m3");
  }

  @Test
  void embedsABatchInOrderThroughTheCatalogueEndpoint() {
    answer =
        """
        {"model":"bge-m3","data":[
          {"index":1,"embedding":[0.5,0.25]},{"index":0,"embedding":[1.0,2.0]}],
         "usage":{"prompt_tokens":7}}""";

    EmbeddingResult result = client(Optional.of(endpoint())).embed(List.of("a", "b"), null);

    assertThat(path.get()).isEqualTo("/v1/embeddings");
    assertThat(auth.get()).isEqualTo("Bearer k-123");
    assertThat(body.get()).contains("\"model\":\"bge-m3\"").contains("\"input\":[\"a\",\"b\"]");
    assertThat(result.model()).isEqualTo("bge-m3");
    assertThat(result.promptTokens()).isEqualTo(7);
    assertThat(result.dimensions()).isEqualTo(2);
    assertThat(result.vectors().getFirst()).containsExactly(1.0f, 2.0f);
    assertThat(result.vectors().get(1)).containsExactly(0.5f, 0.25f);
  }

  @Test
  void failsClosedWhenNoEmbeddingModelIsConfigured() {
    assertThatThrownBy(() -> client(Optional.empty()).embed(List.of("a"), null))
        .isInstanceOf(EmbeddingException.class)
        .hasMessageContaining("no embedding model");
  }

  @Test
  void aProviderFailureNeverLeaksTheKeyOrTheInput() {
    status = 401;
    answer = "{\"message\":\"Unable to authenticate you\"}";

    assertThatThrownBy(() -> client(Optional.of(endpoint())).embed(List.of("secret-text"), null))
        .isInstanceOf(EmbeddingException.class)
        .hasMessageNotContaining("k-123")
        .hasMessageNotContaining("secret-text");
  }

  @Test
  void aWrongCountOfVectorsIsRefused() {
    answer = "{\"data\":[{\"index\":0,\"embedding\":[1.0]}]}";

    assertThatThrownBy(() -> client(Optional.of(endpoint())).embed(List.of("a", "b"), null))
        .isInstanceOf(EmbeddingException.class)
        .hasMessageContaining("unexpected shape");
  }

  @Test
  void refusesEmptyBlankAndOversizedBatches() {
    EmbeddingClientImpl c = client(Optional.of(endpoint()));
    assertThatThrownBy(() -> c.embed(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> c.embed(List.of(" "), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> c.embed(java.util.Collections.nCopies(129, "x"), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theVersionIsAddedOnlyWhenAbsent() {
    assertThat(EmbeddingClientImpl.embeddingsUrl("https://h.example/"))
        .isEqualTo("https://h.example/v1/embeddings");
    assertThat(EmbeddingClientImpl.embeddingsUrl("http://h:11434/v1"))
        .isEqualTo("http://h:11434/v1/embeddings");
  }

  private record FixedResolver(Optional<ModelEndpoint> endpoint)
      implements com.krizaka.sovkora.core.domain.ports.outbound.ModelEndpointResolver {
    @Override
    public Optional<ModelEndpoint> resolve(String modelName) {
      return endpoint;
    }

    @Override
    public Optional<ModelEndpoint> resolveDefault(String category) {
      return "embedding".equals(category) ? endpoint : Optional.empty();
    }
  }

  @Test
  void aSpentBudgetRefusesAPaidEmbeddingWith429AndNeverCallsTheProvider() {
    var guard = new com.krizaka.sovkora.adapter.springai.spend.SpendGuard(0.0, 0.01, 0.0002, 0.6);
    var paid = new ModelEndpoint("do-inference", "https://127.0.0.2.invalid", "k", "bge-m3");
    var client =
        new EmbeddingClientImpl(new FixedResolver(Optional.of(paid)), RestClient.builder(), guard);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.embed(List.of("hello"), null))
        .isInstanceOf(
            com.krizaka.sovkora.adapter.springai.spend.SpendBudgetExceededException.class);
    assertThat(body.get()).isNull();
  }
}
