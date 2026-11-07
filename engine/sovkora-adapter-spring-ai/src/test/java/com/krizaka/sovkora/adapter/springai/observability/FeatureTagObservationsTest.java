package com.krizaka.sovkora.adapter.springai.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.adapter.springai.chat.SpringAiChatInvocation;
import com.krizaka.sovkora.adapter.springai.prompt.SpringPreparedPrompt;
import com.krizaka.sovkora.adapter.springai.routing.UniversalProxyChatProvider;
import com.krizaka.sovkora.core.domain.model.chat.ChatRoute;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;

/**
 * ADR-094 slice 3 (#171): every {@code gen_ai} observation wears the feature key of the turn as a
 * low-cardinality tag ({@code orazaka.feature}), {@code none} when no feature asked — so the cost
 * dashboard reads tokens per feature without a second source.
 */
class FeatureTagObservationsTest {

  private static final String TAG = "orazaka.feature";

  private HttpServer server;
  private final List<Observation.Context> stopped = Collections.synchronizedList(new ArrayList<>());
  private ObservationRegistry registry;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          String request =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          boolean stream = request.contains("\"stream\":true");
          String json =
              "{\"id\":\"c1\",\"object\":\"chat.completion"
                  + (stream ? ".chunk" : "")
                  + "\",\"created\":1,\"model\":\"stub-model\",\"choices\":[{\"index\":0,"
                  + "\"finish_reason\":\"stop\",\""
                  + (stream ? "delta" : "message")
                  + "\":{\"role\":\"assistant\",\"content\":\"ok\"}}],"
                  + "\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3,\"total_tokens\":10}}";
          byte[] body =
              (stream ? "data: " + json + "\n\ndata: [DONE]\n\n" : json)
                  .getBytes(StandardCharsets.UTF_8);
          exchange
              .getResponseHeaders()
              .add("Content-Type", stream ? "text/event-stream" : "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    registry = ObservationRegistry.create();
    registry.observationConfig().observationFilter(new FeatureTagObservationFilter());
    registry
        .observationConfig()
        .observationHandler(
            new ObservationHandler<>() {
              @Override
              public boolean supportsContext(Observation.Context context) {
                return true;
              }

              @Override
              public void onStop(Observation.Context context) {
                stopped.add(context);
              }
            });
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private SpringAiChatInvocation invocation() {
    ChatModel model =
        new UniversalProxyChatProvider(registry)
            .chatModel("http://127.0.0.1:" + server.getAddress().getPort(), "k", "stub-model");
    return new SpringAiChatInvocation(
        model,
        (provider, modelName, key) -> model,
        ToolCallingManager.builder().observationRegistry(registry).build(),
        feature -> 4,
        registry);
  }

  /** A streamed observation stops when the stream's last signal lands: wait for it, briefly. */
  private String featureOfChat() {
    long deadline = System.nanoTime() + 5_000_000_000L;
    while (System.nanoTime() < deadline) {
      synchronized (stopped) {
        for (Observation.Context c : stopped) {
          if ("gen_ai.client.operation".equals(c.getName())) {
            return c.getLowCardinalityKeyValue(TAG).getValue();
          }
        }
      }
      Thread.onSpinWait();
    }
    throw new AssertionError("no gen_ai.client.operation");
  }

  @Test
  void aChatCallWearsTheFeatureKeyOfItsTurn() {
    invocation()
        .call(
            new SpringPreparedPrompt(new Prompt("hello"), "assistant.chat"), ChatRoute.platform());

    assertThat(featureOfChat()).isEqualTo("assistant.chat");
  }

  @Test
  void aStreamedChatWearsTheFeatureKeyOfItsTurn() {
    invocation().stream(
            new SpringPreparedPrompt(new Prompt("hello"), "assistant.stream"), ChatRoute.platform())
        .collectList()
        .block();

    assertThat(featureOfChat()).isEqualTo("assistant.stream");
  }

  @Test
  void aCallWithNoFeatureWearsNone() {
    invocation().call(new SpringPreparedPrompt(new Prompt("hello")), ChatRoute.platform());

    assertThat(featureOfChat()).isEqualTo("none");
  }

  @Test
  void aModelCalledOutsideAnyTurnWearsNone() {
    new UniversalProxyChatProvider(registry)
        .chatModel("http://127.0.0.1:" + server.getAddress().getPort(), "k", "stub-model")
        .call(new Prompt("hello"));

    assertThat(featureOfChat()).isEqualTo("none");
  }

  @Test
  void theTagIsOnlyOnGenAiObservations() {
    Observation.createNotStarted("http.server.requests", registry).observe(() -> "x");

    Observation.Context other =
        stopped.stream()
            .filter(c -> "http.server.requests".equals(c.getName()))
            .findFirst()
            .orElseThrow();
    assertThat(other.getLowCardinalityKeyValue(TAG)).isNull();
  }
}
