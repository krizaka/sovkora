package com.krizaka.sovkora.adapter.springai.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.adapter.springai.routing.UniversalProxyChatProvider;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * ADR-094 slice 3: every model the engine builds carries the host's {@link ObservationRegistry}, so
 * Spring AI records {@code gen_ai.client.operation} for it, and no user content reaches the
 * telemetry (ADR-064).
 */
class ModelObservationsTest {

  private static final String USER_TEXT = "my-private-question-0xC0FFEE";
  private static final String ANSWER = "the-private-answer-0xBEEF";

  private HttpServer server;
  private final List<Observation.Context> stopped = new ArrayList<>();
  private ObservationRegistry registry;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          byte[] body =
              ("{\"id\":\"c1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"stub-model\","
                      + "\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":"
                      + "{\"role\":\"assistant\",\"content\":\""
                      + ANSWER
                      + "\"}}],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3,"
                      + "\"total_tokens\":10}}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    registry = ObservationRegistry.create();
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

  private ChatModel model() {
    return new UniversalProxyChatProvider(registry)
        .chatModel("http://127.0.0.1:" + server.getAddress().getPort(), "k", "stub-model");
  }

  @Test
  void aChatCallIsObservedWithItsModelAndOperation() {
    model().call(new Prompt(USER_TEXT));

    Observation.Context chat =
        stopped.stream()
            .filter(c -> "gen_ai.client.operation".equals(c.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no gen_ai.client.operation: " + names()));
    assertThat(chat.getLowCardinalityKeyValue("gen_ai.operation.name").getValue())
        .isEqualTo("chat");
    assertThat(chat.getLowCardinalityKeyValue("gen_ai.request.model").getValue())
        .isEqualTo("stub-model");
  }

  @Test
  void noUserContentReachesTheTelemetry() {
    model().call(new Prompt(USER_TEXT));

    assertThat(stopped).isNotEmpty();
    for (Observation.Context c : stopped) {
      String all = c.getLowCardinalityKeyValues() + " " + c.getHighCardinalityKeyValues();
      assertThat(all).doesNotContain(USER_TEXT).doesNotContain(ANSWER);
    }
  }

  @Test
  void withoutARegistryTheModelStillWorks() {
    ChatModel plain =
        new UniversalProxyChatProvider()
            .chatModel("http://127.0.0.1:" + server.getAddress().getPort(), "k", "stub-model");
    assertThat(plain.call(new Prompt(USER_TEXT)).getResult().getOutput().getText())
        .isEqualTo(ANSWER);
    assertThat(stopped).isEmpty();
  }

  private List<String> names() {
    return stopped.stream().map(Observation.Context::getName).toList();
  }
}
