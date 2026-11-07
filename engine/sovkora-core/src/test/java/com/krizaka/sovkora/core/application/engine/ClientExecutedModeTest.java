package com.krizaka.sovkora.core.application.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.application.TestPromptAssembly;
import com.krizaka.sovkora.core.application.pipeline.EnginePipelineBridge;
import com.krizaka.sovkora.core.application.pipeline.EngineStreamBridge;
import com.krizaka.sovkora.core.application.security.ClientToolGate;
import com.krizaka.sovkora.core.domain.model.Authority;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.ChatCompletion;
import com.krizaka.sovkora.core.domain.model.chat.ChatRoute;
import com.krizaka.sovkora.core.domain.model.chat.ClientTool;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import com.krizaka.sovkora.core.domain.model.chat.ToolCall;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.AccessAudit;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatInvocation;
import com.krizaka.sovkora.core.domain.ports.outbound.DomainEventPublisher;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.SpeechSynthesis;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolPermissionProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolResolution;
import com.krizaka.sovkora.core.domain.ports.outbound.UserCredentialsProvider;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * Client-executed tool mode (ADR-093 §4) through the engine: the model's calls go back to the
 * client, screened by ADR-091 on every turn, and the engine falls back to nothing silently.
 */
class ClientExecutedModeTest {

  private static final ClientTool READ = new ClientTool("read_file", "reads", "{}");
  private static final ClientTool SHELL = new ClientTool("shell", "runs", "{}");

  private final ToolPermissionProvider permissions = mock(ToolPermissionProvider.class);
  private final AccessAudit audit = mock(AccessAudit.class);
  private final ClientToolGate gate = new ClientToolGate(permissions, audit);
  private final List<String> events = new ArrayList<>();

  private List<ToolCall> asked = List.of();
  private RuntimeException failure;
  private Engine engine;

  /** Stands for the adapter: offers the gate's tools, never runs the engine's own. */
  private final ToolResolution resolution =
      new ToolResolution() {
        @Override
        public ResolvedTools resolve(InternalChatRequest request) {
          events.add("server-tools");
          return ResolvedTools.none();
        }

        @Override
        public ResolvedTools resolveClientTools(InternalChatRequest request) {
          events.add("client-tools");
          return new ResolvedTools(
              gate.offer(Actor.from(request.context()).orElse(null), request.clientTools()),
              List.of());
        }

        @Override
        public List<ToolCall> screenClientCalls(InternalChatRequest request, List<ToolCall> calls) {
          return gate.screen(
              Actor.from(request.context()).orElse(null), request.clientTools(), calls);
        }
      };

  private final ChatInvocation model =
      new ChatInvocation() {
        @Override
        public ChatCompletion call(PreparedPrompt prompt, ChatRoute route) {
          if (failure != null) {
            throw failure;
          }
          return new ChatCompletion("", TokenUsage.none(), asked);
        }

        @Override
        public Flux<ChatCompletion> stream(PreparedPrompt prompt, ChatRoute route) {
          return Flux.just(new ChatCompletion("", TokenUsage.none(), asked));
        }
      };

  @BeforeEach
  void setUp() {
    var properties =
        new CoreProperties(
            "ollama",
            new CoreProperties.RagConfig(false, null, 3),
            new CoreProperties.McpConfig(List.of()),
            new CoreProperties.OrchestrationConfig(
                false,
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.RoutingConfig(null, null)),
            null,
            null,
            null,
            null);
    var catalog = mock(ModelCatalogProvider.class);
    when(catalog.getActiveChatModel()).thenReturn(Optional.of("qwen2.5-coder:7b"));
    var credentials = mock(UserCredentialsProvider.class);
    var bridge = new EnginePipelineBridge(catalog, resolution, new TestPromptAssembly());
    engine =
        new Engine(
            model,
            mock(SpeechSynthesis.class),
            properties,
            List.of(),
            null,
            mock(DomainEventPublisher.class),
            catalog,
            bridge,
            new EngineStreamBridge(model, bridge, credentials),
            credentials);
    when(permissions.requiredRoles(any())).thenReturn(Optional.of(Set.of("ROLE_DEV")));
  }

  private static InternalChatRequest turn(List<ClientTool> tools) {
    var context = new Context("dev-1", "c-1", Map.of(), Set.of(new Authority("ROLE_DEV")));
    return new InternalChatRequest("fix it", List.of(), null, context, false, tools);
  }

  @Test
  @DisplayName(
      "the model's calls go back to the client, and the engine's own tools are not resolved")
  void callsGoBack() {
    asked = List.of(new ToolCall("c1", "read_file", "{\"path\":\"a\"}"));

    var out = engine.chat(turn(List.of(READ)));

    assertThat(out.toolCalls()).containsExactly(asked.getFirst());
    assertThat(events).containsExactly("client-tools");
  }

  @Test
  @DisplayName("a call to a tool that was never declared does not go back")
  void anUndeclaredCallIsDropped() {
    asked = List.of(new ToolCall("c1", "shell", "{}"), new ToolCall("c2", "read_file", "{}"));

    var out = engine.chat(turn(List.of(READ)));

    assertThat(out.toolCalls()).extracting(ToolCall::name).containsExactly("read_file");
    assertThat(SHELL.name()).isEqualTo("shell");
  }

  @Test
  @DisplayName("the usual mode returns no call: the engine's own tools stay server-side")
  void usualModeReturnsNoCall() {
    asked = List.of(new ToolCall("c1", "read_file", "{}"));

    var out = engine.chat(InternalChatRequest.simple("hello"));

    assertThat(out.toolCalls()).isEmpty();
    assertThat(events).containsExactly("server-tools");
  }

  @Test
  @DisplayName("a model that fails with tools is not retried without them")
  void noSilentFallbackWithoutTools() {
    failure = new IllegalStateException("tools not supported");

    assertThatThrownBy(() -> engine.chat(turn(List.of(READ))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("a streamed turn returns the screened calls on its chunk")
  void streamedCalls() {
    asked = List.of(new ToolCall("c1", "read_file", "{}"), new ToolCall("c2", "shell", "{}"));

    var chunks = engine.stream(turn(List.of(READ))).collectList().block();

    assertThat(chunks).hasSize(2);
    assertThat(chunks.getFirst().toolCalls())
        .extracting(ToolCall::name)
        .containsExactly("read_file");
  }
}
