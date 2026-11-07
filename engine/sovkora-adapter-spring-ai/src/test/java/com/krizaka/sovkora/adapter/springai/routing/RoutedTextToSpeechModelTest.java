package com.krizaka.sovkora.adapter.springai.routing;

import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelEndpointResolver;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import reactor.core.publisher.Flux;

/**
 * Speech is routed by availability, from the catalogue: the model a request names when its provider
 * answers, the category's default otherwise — so speech keeps working when an optional host (a GPU
 * pod serving Kokoro) is down.
 */
class RoutedTextToSpeechModelTest {

  private static final ModelEndpoint PIPER =
      new ModelEndpoint(
          "localai", "http://localai:8085", "not-required", "piper-en-medium-ljspeech");
  private static final ModelEndpoint KOKORO =
      new ModelEndpoint("gpu-speech", "http://gpu-relay:8086/v1", "not-required", "kokoro-82m");

  /** A provider: answers with its own name as bytes, or refuses the connection when it is down. */
  private static final class FakeProvider implements TextToSpeechModel {
    final String name;
    final boolean up;
    final List<String> models = new ArrayList<>();

    FakeProvider(String name, boolean up) {
      this.name = name;
      this.up = up;
    }

    @Override
    public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
      models.add(prompt.getOptions().getModel());
      if (!up) {
        throw new IllegalStateException(
            "Request failed", new ConnectException("Connection refused"));
      }
      return new TextToSpeechResponse(List.of(new Speech(name.getBytes())));
    }

    @Override
    public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
      return Flux.just(call(prompt));
    }
  }

  private static ModelEndpointResolver catalogue() {
    return new ModelEndpointResolver() {
      @Override
      public Optional<ModelEndpoint> resolve(String modelName) {
        return Map.of("kokoro-82m", KOKORO, "piper-en-medium-ljspeech", PIPER).entrySet().stream()
            .filter(e -> e.getKey().equals(modelName))
            .map(Map.Entry::getValue)
            .findFirst();
      }

      @Override
      public Optional<ModelEndpoint> resolveDefault(String category) {
        return Optional.of(PIPER);
      }
    };
  }

  private static TextToSpeechPrompt prompt(String model) {
    return new TextToSpeechPrompt(
        "Hello", OpenAiAudioSpeechOptions.builder().responseFormat("mp3").model(model).build());
  }

  @Test
  void aRequestNamingNoModelGoesToTheDefault() {
    FakeProvider piper = new FakeProvider("piper", true);
    var tts = new RoutedTextToSpeechModel(catalogue(), e -> e == PIPER ? piper : null);

    byte[] audio = tts.call(prompt(null)).getResult().getOutput();

    assertArrayEquals("piper".getBytes(), audio);
    assertEquals(List.of("piper-en-medium-ljspeech"), piper.models);
  }

  @Test
  void theNamedModelAnswersWhenItsHostIsUp() {
    FakeProvider kokoro = new FakeProvider("kokoro", true);
    var tts = new RoutedTextToSpeechModel(catalogue(), e -> e == KOKORO ? kokoro : null);

    assertArrayEquals("kokoro".getBytes(), tts.call(prompt("kokoro-82m")).getResult().getOutput());
    assertEquals(List.of("kokoro-82m"), kokoro.models);
  }

  @Test
  void withNoPodTheDefaultStillSpeaks_underItsOwnModelName() {
    FakeProvider kokoro = new FakeProvider("kokoro", false);
    FakeProvider piper = new FakeProvider("piper", true);
    var tts = new RoutedTextToSpeechModel(catalogue(), e -> e == KOKORO ? kokoro : piper);

    byte[] audio = tts.call(prompt("kokoro-82m")).getResult().getOutput();

    assertArrayEquals("piper".getBytes(), audio);
    assertEquals(List.of("piper-en-medium-ljspeech"), piper.models);
  }

  @Test
  void aFailureThatIsNotAnUnreachableHostIsNotHidden() {
    TextToSpeechModel broken =
        new TextToSpeechModel() {
          @Override
          public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
            throw new IllegalArgumentException("unknown voice");
          }

          @Override
          public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
            return Flux.error(new IllegalArgumentException("unknown voice"));
          }
        };
    var tts = new RoutedTextToSpeechModel(catalogue(), e -> broken);

    assertThrows(IllegalArgumentException.class, () -> tts.call(prompt("kokoro-82m")));
  }
}
