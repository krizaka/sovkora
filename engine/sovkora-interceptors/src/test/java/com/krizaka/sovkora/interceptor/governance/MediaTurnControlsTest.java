package com.krizaka.sovkora.interceptor.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.billing.domain.model.CreditHoldResponse;
import com.krizaka.billing.domain.model.EntitlementSnapshot;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.billing.domain.port.EntitlementProvider;
import com.krizaka.sovkora.adapter.springai.prompt.SpringPreparedPrompt;
import com.krizaka.sovkora.core.application.engine.Engine;
import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.*;
import com.krizaka.sovkora.core.application.routing.SemanticRoutingEngine;
import com.krizaka.sovkora.core.domain.event.ChatCompletedEvent;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.InterceptorConfig;
import com.krizaka.sovkora.core.domain.model.PipelineConfig;
import com.krizaka.sovkora.core.domain.model.RoutingMode;
import com.krizaka.sovkora.core.domain.model.chat.ChatCompletion;
import com.krizaka.sovkora.core.domain.model.chat.ChatRoute;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatInvocation;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.UserCredentialsProvider;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import com.krizaka.sovkora.core.infrastructure.config.SecurityProperties;
import com.krizaka.sovkora.interceptor.reformulation.RefinerInterceptor;
import com.krizaka.sovkora.interceptor.validation.SafetyInterceptor;
import com.krizaka.sovkora.jobs.domain.model.JobCommand;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;

/**
 * A chat turn carrying an image, through the real engine, the real pipeline executor and the real
 * controls (ADR-063, audit #21).
 *
 * <p>The prompt carries its image the way every caller of the chat API can: inline, in the text.
 * Until ADR-063 the engine skipped the pipeline for such a turn, and four controls went with it.
 * One test per control, because four different things were bypassed and one assertion covering all
 * of them can pass for the wrong reason — a refusal from the first control would satisfy a test
 * written for the fourth.
 *
 * <p>Nothing in the path under test is a double except its edges: the model, the billing ports and
 * the row source of the pipeline order. The engine, the bridge, the executor and the four
 * interceptors are the production classes.
 */
class MediaTurnControlsTest {

  private static final String ACTOR = "550e8400-e29b-41d4-a716-446655440063";

  /** A 1×1 PNG, inline, the way a caller puts an image in the text it sends. */
  private static final String IMAGE_MARKER =
      "[posterBase64: iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==]";

  /** What a user types beside a photographed lease: no term any text guard could match. */
  private static final String WORDS = "t'en penses quoi ?";

  private static final String SCOPE_REFUSAL = "Je rédige des documents, je ne donne pas d'avis.";
  private static final String CRISIS_RESPONSE = "Des personnes formées répondent : 3114.";

  private final ChatModel model = mock(ChatModel.class);
  private final EntitlementProvider entitlements = mock(EntitlementProvider.class);
  private final CreditAuthorizationClient credits = mock(CreditAuthorizationClient.class);
  private final List<Object> published = new ArrayList<>();

  @Test
  @DisplayName("1 · the orchestration switch refuses a turn carrying an image")
  void theOrchestrationSwitchIsHonoured() {
    Engine engine = engine(orchestration(false), new SecurityProperties(false));

    assertThatThrownBy(() -> engine.chat(mediaTurn(Map.of())))
        .isInstanceOf(PipelineDisabledException.class);
    verify(model, never()).call(any(Prompt.class));
  }

  @Test
  @DisplayName("2 · disable-ai refuses a turn carrying an image")
  void disableAiIsHonoured() {
    Engine engine = engine(orchestration(true), new SecurityProperties(true));

    assertThatThrownBy(() -> engine.chat(mediaTurn(Map.of())))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("RefinerInterceptor");
    verify(model, never()).call(any(Prompt.class));
  }

  @Test
  @DisplayName("3a · the scope guard runs, sees the image, and refuses what it cannot read")
  void theScopeGuardSeesTheMedia() {
    Engine engine = engine(orchestration(true), new SecurityProperties(false));
    Map<String, Object> declared = new HashMap<>();
    declared.put(JobCommand.SCOPE_REFUSED_TERMS_KEY, "conseil juridique, bail");
    declared.put(JobCommand.SCOPE_REFUSAL_KEY, SCOPE_REFUSAL);

    // Refused as unexamined, not as out of scope: the words match nothing, so only a guard that
    // knew an image was in the turn can have stopped it.
    assertThatThrownBy(() -> engine.chat(mediaTurn(declared)))
        .isInstanceOf(PipelineShortCircuitException.class)
        .hasMessage(SCOPE_REFUSAL)
        .satisfies(
            refusal -> {
              PipelineShortCircuitException stop = (PipelineShortCircuitException) refusal;
              assertThat(stop.interceptorId()).isEqualTo("ScopeGuardInterceptor");
              assertThat(stop.reason()).isEqualTo("subject_not_examinable");
            });
    verify(model, never()).call(any(Prompt.class));
  }

  @Test
  @DisplayName("3b · the crisis guard runs, sees the image, and answers the reviewed text")
  void theCrisisGuardSeesTheMedia() {
    Engine engine = engine(orchestration(true), new SecurityProperties(false));
    Map<String, Object> declared = new HashMap<>();
    declared.put(JobCommand.SAFETY_CRISIS_TERMS_KEY, "en finir, plus vivre");
    declared.put(JobCommand.SAFETY_RESPONSE_KEY, CRISIS_RESPONSE);

    assertThatThrownBy(() -> engine.chat(mediaTurn(declared)))
        .isInstanceOf(PipelineShortCircuitException.class)
        .hasMessage(CRISIS_RESPONSE)
        .satisfies(
            refusal -> {
              PipelineShortCircuitException stop = (PipelineShortCircuitException) refusal;
              assertThat(stop.interceptorId()).isEqualTo("SafetyInterceptor");
              assertThat(stop.reason()).isEqualTo("subject_not_examinable");
            });
    verify(model, never()).call(any(Prompt.class));
  }

  @Test
  @DisplayName("4 · the entitlement check takes a credit hold, and the completed turn carries it")
  void aCreditHoldIsTaken() {
    Engine engine = engine(orchestration(true), new SecurityProperties(false));
    when(model.call(any(Prompt.class)))
        .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Un bail.")))));

    engine.chat(mediaTurn(Map.of()));

    // The hold is settled from the completed-turn event; a turn that published none was never
    // billed, which is what every media chat turn was until ADR-063.
    assertThat(published)
        .filteredOn(ChatCompletedEvent.class::isInstance)
        .map(event -> ((ChatCompletedEvent) event).holdId())
        .containsExactly("hold-media-1");
  }

  private InternalChatRequest mediaTurn(Map<String, Object> declarations) {
    Context context = new Context(ACTOR, "conv-63", declarations, Set.of());
    return new InternalChatRequest(WORDS + " " + IMAGE_MARKER, List.of(), null, context);
  }

  /** The production engine over the production pipeline, with the seeded chain's shape. */
  private Engine engine(
      CoreProperties.OrchestrationConfig orchestration, SecurityProperties security) {
    // An entitled actor with credits, for every test: a gate that failed on a missing stub would be
    // swallowed by the executor, and a refusal test must not depend on what the gate did.
    when(entitlements.forActor(ACTOR))
        .thenReturn(
            new EntitlementSnapshot(
                ACTOR,
                "pro",
                Map.of("capability.chat", "true"),
                500L,
                Instant.now().plusSeconds(60)));
    when(credits.hold(any()))
        .thenReturn(new CreditHoldResponse("hold-media-1", true, false, 2, 498, 1));
    CoreProperties properties =
        new CoreProperties(
            "ollama",
            new CoreProperties.RagConfig(false, null, 3),
            new CoreProperties.McpConfig(List.of()),
            orchestration,
            null,
            null,
            null,
            null);
    PromptContextInterceptor refiner =
        new RefinerInterceptor(
            mock(com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient.class),
            properties,
            new ByteArrayResource("system".getBytes()),
            new ByteArrayResource("{rawQuery}".getBytes()));
    List<PromptContextInterceptor> chain =
        List.of(
            new SafetyInterceptor(),
            new ScopeGuardInterceptor(),
            new EntitlementInterceptor(
                entitlements,
                credits,
                mock(com.krizaka.billing.domain.port.UnmeteredTurnRepository.class)),
            refiner);
    // Phase 1 holds the two guards and Phase 2 the gate and an AI-dependent stage — the order the
    // seed and PipelineRegistry give them in a running service.
    PipelineRegistry registry = mock(PipelineRegistry.class);
    when(registry.getConfig(any()))
        .thenReturn(
            new PipelineConfig(
                "default",
                List.of(
                    new InterceptorConfig("SafetyInterceptor", "Safety", 0, true, ""),
                    new InterceptorConfig("ScopeGuardInterceptor", "Scope", 1, true, "")),
                List.of(
                    new InterceptorConfig("EntitlementInterceptor", "Entitlement", 4, true, ""),
                    new InterceptorConfig("RefinerInterceptor", "Refiner", 8, true, "")),
                List.of()));
    DynamicPipelineExecutor pipeline =
        new DynamicPipelineExecutor(
            chain,
            registry,
            mock(SemanticRoutingEngine.class),
            security,
            properties,
            new SimpleMeterRegistry(),
            published::add);

    ModelCatalogProvider catalog = mock(ModelCatalogProvider.class);
    when(catalog.getActiveChatModel()).thenReturn(Optional.of("llava"));
    EnginePipelineBridge bridge =
        new EnginePipelineBridge(
            catalog,
            request ->
                com.krizaka.sovkora.core.domain.ports.outbound.ToolResolution.ResolvedTools.none(),
            new com.krizaka.sovkora.adapter.springai.prompt.SpringAiPromptAssembly());
    UserCredentialsProvider userCredentials = mock(UserCredentialsProvider.class);
    return new Engine(
        over(model),
        null,
        properties,
        List.of(),
        pipeline,
        published::add,
        catalog,
        bridge,
        new EngineStreamBridge(over(model), bridge, userCredentials),
        userCredentials);
  }

  /** The port over the scripted model; the Spring AI implementation is tested in its adapter. */
  private static ChatInvocation over(ChatModel model) {
    return new ChatInvocation() {
      @Override
      public ChatCompletion call(PreparedPrompt prompt, ChatRoute route) {
        var out = model.call(((SpringPreparedPrompt) prompt).prompt()).getResult().getOutput();
        return new ChatCompletion(out.getText(), null);
      }

      @Override
      public reactor.core.publisher.Flux<ChatCompletion> stream(
          PreparedPrompt prompt, ChatRoute route) {
        throw new UnsupportedOperationException("not streamed here");
      }
    };
  }

  private static CoreProperties.OrchestrationConfig orchestration(boolean enabled) {
    return new CoreProperties.OrchestrationConfig(
        enabled,
        new CoreProperties.InterceptorConfig("ollama", "llama3.2:3b", 0.7),
        new CoreProperties.InterceptorConfig("ollama", "llama3.2:3b", 0.0),
        new CoreProperties.RoutingConfig(RoutingMode.DETERMINISTIC, null));
  }
}
