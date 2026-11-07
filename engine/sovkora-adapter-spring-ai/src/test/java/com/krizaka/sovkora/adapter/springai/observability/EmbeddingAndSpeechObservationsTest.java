package com.krizaka.sovkora.adapter.springai.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.adapter.springai.client.EmbeddingClientImpl;
import com.krizaka.sovkora.adapter.springai.routing.RoutedTextToSpeechModel;
import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelEndpointResolver;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.embedding.observation.EmbeddingModelMeterObservationHandler;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import reactor.core.publisher.Flux;

/**
 * ADR-094 slice 3 (#171): the embeddings and the speech the engine builds by hand are observed like
 * the chat models Spring AI builds — {@code gen_ai.client.operation} with the same keys — and no
 * text of the user reaches the telemetry (ADR-064).
 */
class EmbeddingAndSpeechObservationsTest {

  private static final String INPUT = "my-private-input-0xC0FFEE";

  private HttpServer server;
  private final List<Observation.Context> stopped = new ArrayList<>();
  private final MeterRegistry meters = new SimpleMeterRegistry();
  private ObservationRegistry registry;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/embeddings",
        exchange -> {
          byte[] out =
              ("{\"model\":\"bge-m3\",\"data\":[{\"index\":0,\"embedding\":[1.0,2.0]}],"
                      + "\"usage\":{\"prompt_tokens\":5}}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, out.length);
          exchange.getResponseBody().write(out);
          exchange.close();
        });
    server.start();
    registry = ObservationRegistry.create();
    registry
        .observationConfig()
        .observationHandler(new EmbeddingModelMeterObservationHandler(meters));
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

  private Observation.Context operation() {
    return stopped.stream()
        .filter(c -> "gen_ai.client.operation".equals(c.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no gen_ai.client.operation observed: " + stopped));
  }

  @Test
  void anEmbeddingCallIsObservedWithItsModelAndItsTokens() {
    ModelEndpoint endpoint =
        new ModelEndpoint(
            "do-inference", "http://127.0.0.1:" + server.getAddress().getPort(), "k", "bge-m3");
    ModelEndpointResolver resolver = resolverOf(endpoint);

    new EmbeddingClientImpl(resolver, org.springframework.web.client.RestClient.builder(), registry)
        .embed(List.of(INPUT), null);

    Observation.Context embedding = operation();
    assertThat(embedding.getLowCardinalityKeyValue("gen_ai.operation.name").getValue())
        .isEqualTo("embedding");
    assertThat(embedding.getLowCardinalityKeyValue("gen_ai.request.model").getValue())
        .isEqualTo("bge-m3");
    assertThat(embedding.getLowCardinalityKeyValue("gen_ai.system").getValue())
        .isEqualTo("do-inference");
    double tokens =
        meters
            .find("gen_ai.client.token.usage")
            .tag("gen_ai.token.type", "input")
            .counter()
            .count();
    assertThat(tokens).isEqualTo(5.0);
  }

  @Test
  void noEmbeddingInputReachesTheTelemetry() {
    ModelEndpoint endpoint =
        new ModelEndpoint(
            "do-inference", "http://127.0.0.1:" + server.getAddress().getPort(), "k", "bge-m3");

    new EmbeddingClientImpl(
            resolverOf(endpoint), org.springframework.web.client.RestClient.builder(), registry)
        .embed(List.of(INPUT), null);

    assertThat(stopped).isNotEmpty();
    for (Observation.Context c : stopped) {
      assertThat(c.getLowCardinalityKeyValues() + " " + c.getHighCardinalityKeyValues())
          .doesNotContain(INPUT);
    }
  }

  @Test
  void aSpeechCallIsObservedAndItsTextStaysOut() {
    ModelEndpoint kokoro = new ModelEndpoint("gpu-speech", "http://gpu:8086/v1", "k", "kokoro-82m");
    TextToSpeechModel provider =
        new TextToSpeechModel() {
          @Override
          public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
            return new TextToSpeechResponse(List.of(new Speech(new byte[] {1})));
          }

          @Override
          public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
            return Flux.just(call(prompt));
          }
        };
    RoutedTextToSpeechModel speech =
        new RoutedTextToSpeechModel(resolverOf(kokoro), endpoint -> provider, registry);

    speech.call(
        new TextToSpeechPrompt(
            INPUT, OpenAiAudioSpeechOptions.builder().model("kokoro-82m").build()));

    Observation.Context observed = operation();
    assertThat(observed.getLowCardinalityKeyValue("gen_ai.operation.name").getValue())
        .isEqualTo("text_to_speech");
    assertThat(observed.getLowCardinalityKeyValue("gen_ai.request.model").getValue())
        .isEqualTo("kokoro-82m");
    assertThat(observed.getLowCardinalityKeyValue("gen_ai.system").getValue())
        .isEqualTo("gpu-speech");
    for (Observation.Context c : stopped) {
      assertThat(c.getLowCardinalityKeyValues() + " " + c.getHighCardinalityKeyValues())
          .doesNotContain(INPUT);
    }
  }

  private static ModelEndpointResolver resolverOf(ModelEndpoint endpoint) {
    return new ModelEndpointResolver() {
      @Override
      public Optional<ModelEndpoint> resolve(String modelName) {
        return Optional.of(endpoint);
      }

      @Override
      public Optional<ModelEndpoint> resolveDefault(String category) {
        return Optional.of(endpoint);
      }
    };
  }
}
