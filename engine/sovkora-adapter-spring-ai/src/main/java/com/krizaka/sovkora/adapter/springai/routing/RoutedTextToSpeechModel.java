package com.krizaka.sovkora.adapter.springai.routing;

import com.krizaka.sovkora.core.domain.model.MediaCategory;
import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelEndpointResolver;
import com.krizaka.sovkora.core.infrastructure.support.CoreException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import reactor.core.publisher.Flux;

/**
 * Primary {@link TextToSpeechModel} bean, backed by the DB-driven speech provider (ADR-030).
 *
 * <p>Replaces the former static {@code AiModelConfiguration.activeTtsModel} bean (built from {@code
 * spring.ai.localai.*}). The speech provider's endpoint is resolved from the DB ({@code
 * orazaka_models} {@code is_default} for category {@code speech} → {@code ai_providers} base-url)
 * and an OpenAI-compatible {@link OpenAiAudioSpeechModel} is built against it. A request that names
 * a speech model of the catalogue goes to that model's provider while it answers, and to the
 * default otherwise.
 *
 * @see com.krizaka.sovkora.core.infrastructure.provider.model.CatalogModelEndpointResolver
 */
public class RoutedTextToSpeechModel implements TextToSpeechModel {

  private static final Logger logger = LoggerFactory.getLogger(RoutedTextToSpeechModel.class);

  private final ModelEndpointResolver resolver;
  private final Function<ModelEndpoint, TextToSpeechModel> providers;
  private final ObservationRegistry observations;

  public RoutedTextToSpeechModel(ModelEndpointResolver resolver) {
    this(resolver, ObservationRegistry.NOOP);
  }

  /**
   * @param observations the host's registry: Spring AI 2.0 has no observation for speech, so each
   *     provider call is a {@code gen_ai.client.operation} recorded here, with the model and the
   *     provider — never the text (ADR-064, ADR-094)
   */
  public RoutedTextToSpeechModel(ModelEndpointResolver resolver, ObservationRegistry observations) {
    this(resolver, RoutedTextToSpeechModel::openAiCompatible, observations);
  }

  RoutedTextToSpeechModel(
      ModelEndpointResolver resolver, Function<ModelEndpoint, TextToSpeechModel> providers) {
    this(resolver, providers, ObservationRegistry.NOOP);
  }

  /** For tests: the provider of each endpoint, and the registry whose observations are read. */
  public RoutedTextToSpeechModel(
      ModelEndpointResolver resolver,
      Function<ModelEndpoint, TextToSpeechModel> providers,
      ObservationRegistry observations) {
    this.observations = Objects.requireNonNull(observations, "ObservationRegistry cannot be null");
    this.resolver = Objects.requireNonNull(resolver, "ModelEndpointResolver cannot be null");
    this.providers = Objects.requireNonNull(providers, "providers cannot be null");
  }

  /**
   * Routes by availability, from the catalogue: the speech model the request names, when the
   * catalogue knows it and its provider answers; the category's default ({@code is_default})
   * otherwise. A host that is only up some of the time (a GPU pod serving Kokoro) can never take
   * speech down with it: an unreachable provider falls back to the default, any other failure is
   * the caller's to see.
   */
  @Override
  public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
    ModelEndpoint fallback =
        resolver
            .resolveDefault(MediaCategory.SPEECH.value())
            .orElseThrow(
                () -> new CoreException("No speech model endpoint resolved (category=speech)."));
    String requested = prompt.getOptions() == null ? null : prompt.getOptions().getModel();
    Optional<ModelEndpoint> named =
        requested == null || requested.isBlank() ? Optional.empty() : resolver.resolve(requested);
    if (named.isPresent() && !named.get().equals(fallback)) {
      try {
        return speak(named.get(), prompt);
      } catch (RuntimeException failure) {
        if (!unreachable(failure)) {
          throw failure;
        }
        logger.warn(
            "speech.fallback requested={} provider={} unreachable — default {} answers",
            named.get().modelName(),
            named.get().providerName(),
            fallback.modelName());
      }
    }
    return speak(fallback, prompt);
  }

  private TextToSpeechResponse speak(ModelEndpoint endpoint, TextToSpeechPrompt prompt) {
    return Observation.createNotStarted("gen_ai.client.operation", observations)
        .lowCardinalityKeyValue("gen_ai.operation.name", "text_to_speech")
        .lowCardinalityKeyValue("gen_ai.system", endpoint.providerName())
        .lowCardinalityKeyValue("gen_ai.request.model", endpoint.modelName())
        .observe(() -> providers.apply(endpoint).call(retarget(prompt, endpoint)));
  }

  @Override
  public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
    return Flux.just(call(prompt));
  }

  /** The same words and format, under the model name the chosen provider knows. */
  private static TextToSpeechPrompt retarget(TextToSpeechPrompt prompt, ModelEndpoint endpoint) {
    String format = null;
    if (prompt.getOptions() instanceof OpenAiAudioSpeechOptions options) {
      format = options.getResponseFormat();
    }
    OpenAiAudioSpeechOptions options =
        OpenAiAudioSpeechOptions.builder()
            .responseFormat(format)
            .model(endpoint.modelName())
            .build();
    return new TextToSpeechPrompt(prompt.getInstructions().getText(), options);
  }

  /**
   * Whether a failure is a provider that did not answer (refused, unreachable), at any depth of its
   * causes.
   */
  static boolean unreachable(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConnectException
          || cause instanceof UnknownHostException
          || cause instanceof NoRouteToHostException) {
        return true;
      }
    }
    return false;
  }

  // Spring AI 2.0 derives the com.openai client from the options via OpenAiSetup, so the speech
  // provider endpoint
  // (base-url + api-key) is set on the OpenAiAudioSpeechOptions.
  private static TextToSpeechModel openAiCompatible(ModelEndpoint endpoint) {
    return OpenAiAudioSpeechModel.builder()
        .options(
            OpenAiAudioSpeechOptions.builder()
                .model(endpoint.modelName())
                .apiKey(endpoint.apiKey())
                .baseUrl(endpoint.baseUrl())
                .build())
        .build();
  }
}
