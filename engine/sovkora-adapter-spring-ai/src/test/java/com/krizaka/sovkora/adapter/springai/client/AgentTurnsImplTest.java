package com.krizaka.sovkora.adapter.springai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.application.engine.Engine;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import com.krizaka.sovkora.core.domain.model.chat.AgentTurnRequest;
import com.krizaka.sovkora.core.domain.model.chat.AgentTurnResult;
import com.krizaka.sovkora.core.domain.model.chat.ClientTool;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatResponse;
import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import com.krizaka.sovkora.core.domain.model.chat.ToolCall;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelEndpointResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentTurnsImplTest {

  private static final String FEATURE = "agent.code.issue-to-pr";
  private static final Context CONTEXT = new Context("u-1", "run-1", Map.of(), Set.of());

  private Engine engine;
  private ModelEndpointResolver models;
  private AgentTurnsImpl turns;

  @BeforeEach
  void setUp() {
    engine = mock(Engine.class);
    models = mock(ModelEndpointResolver.class);
    turns = new AgentTurnsImpl(engine, models);
  }

  @Test
  void aTurnRunsInClientMode_withTheRoutedModel_andReturnsTheScreenedCalls() {
    when(models.resolveForFeature(FEATURE, "code"))
        .thenReturn(Optional.of(new ModelEndpoint("ollama", "http://x", null, "qwen2.5-coder:7b")));
    ToolCall call = new ToolCall("c1", "read_file", "{\"path\":\"a.txt\"}");
    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(
            new InternalChatResponse(
                "reading", "run-1", TokenUsage.reported(10, 5, 15), Map.of(), List.of(call)));
    var request =
        new AgentTurnRequest(
            FEATURE,
            "You are a careful contributor.",
            List.of(
                new AgentTurnRequest.Message("user", "fix #1", null, null),
                new AgentTurnRequest.Message("assistant", "", List.of(call), null),
                new AgentTurnRequest.Message("tool", "contents", null, "c1")),
            List.of(new ClientTool("read_file", "reads", "{}")),
            CONTEXT);

    AgentTurnResult result = turns.turn(request);

    assertThat(result.text()).isEqualTo("reading");
    assertThat(result.toolCalls()).containsExactly(call);
    assertThat(result.usage().promptTokens()).isEqualTo(10);
    var sent = ArgumentCaptor.forClass(InternalChatRequest.class);
    verify(engine).chat(sent.capture());
    InternalChatRequest internal = sent.getValue();
    assertThat(internal.clientToolMode()).isTrue();
    assertThat(internal.clientTools()).extracting(ClientTool::name).containsExactly("read_file");
    assertThat(internal.options().model()).isEqualTo("qwen2.5-coder:7b");
    assertThat(internal.messages())
        .extracting(InternalChatRequest.ChatMessage::role)
        .containsExactly("system", "user", "assistant", "tool");
    assertThat(internal.messages().get(2).toolCalls()).containsExactly(call);
    assertThat(internal.messages().get(3).toolCallId()).isEqualTo("c1");
    assertThat(internal.context()).isEqualTo(CONTEXT);
  }

  @Test
  void aFeatureWithNoRoutedModel_leavesTheEngineItsDefault_andNoToolsStaysClientMode() {
    when(models.resolveForFeature(FEATURE, "code")).thenReturn(Optional.empty());
    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("done", "run-1", Map.of()));

    AgentTurnResult result =
        turns.turn(new AgentTurnRequest(FEATURE, "", List.of(), List.of(), CONTEXT));

    assertThat(result.toolCalls()).isEmpty();
    var sent = ArgumentCaptor.forClass(InternalChatRequest.class);
    verify(engine).chat(sent.capture());
    assertThat(sent.getValue().options()).isNull();
    assertThat(sent.getValue().clientToolMode()).isTrue();
    assertThat(sent.getValue().messages()).isEmpty();
  }

  @Test
  void theCollaboratorsAreRequired() {
    assertThatThrownBy(() -> new AgentTurnsImpl(null, models))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new AgentTurnsImpl(engine, null))
        .isInstanceOf(NullPointerException.class);
  }
}
