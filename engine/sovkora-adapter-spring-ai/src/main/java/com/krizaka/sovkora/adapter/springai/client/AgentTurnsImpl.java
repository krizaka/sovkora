package com.krizaka.sovkora.adapter.springai.client;

import com.krizaka.sovkora.core.application.engine.Engine;
import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import com.krizaka.sovkora.core.domain.model.chat.AgentTurnRequest;
import com.krizaka.sovkora.core.domain.model.chat.AgentTurnResult;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatResponse;
import com.krizaka.sovkora.core.domain.ports.inbound.AgentTurns;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelEndpointResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * {@link AgentTurns} over the engine's client-executed mode (ADR-093 §4): the model is the one the
 * feature key routes to (ADR-029, the {@code code} default when it has no row), the tools are the
 * client's, screened per actor by the engine on every turn.
 */
public class AgentTurnsImpl implements AgentTurns {

  /** The category whose default answers when a feature has no routing row. */
  static final String FALLBACK_CATEGORY = "code";

  private final Engine engine;
  private final ModelEndpointResolver models;

  /**
   * @param engine the engine
   * @param models the routing, by feature key
   */
  public AgentTurnsImpl(Engine engine, ModelEndpointResolver models) {
    this.engine = Objects.requireNonNull(engine, "Engine must not be null");
    this.models = Objects.requireNonNull(models, "ModelEndpointResolver must not be null");
  }

  @Override
  public AgentTurnResult turn(AgentTurnRequest request) {
    List<InternalChatRequest.ChatMessage> messages = new ArrayList<>();
    if (!request.system().isBlank()) {
      messages.add(new InternalChatRequest.ChatMessage("system", request.system()));
    }
    for (AgentTurnRequest.Message message : request.messages()) {
      messages.add(
          new InternalChatRequest.ChatMessage(
              message.role(), message.content(), message.calls(), message.toolCallId()));
    }
    InternalChatRequest internal =
        new InternalChatRequest("", messages, options(request), request.context(), false)
            .withClientTools(request.tools())
            .withFeatureKey(request.featureKey());
    InternalChatResponse answer = engine.chat(internal);
    return new AgentTurnResult(answer.content(), answer.toolCalls(), answer.tokenUsage());
  }

  private EngineChatOptions options(AgentTurnRequest request) {
    return models
        .resolveForFeature(request.featureKey(), FALLBACK_CATEGORY)
        .map(ModelEndpoint::modelName)
        .map(
            name ->
                new EngineChatOptions(name, null, null, ChatOptions.builder().model(name).build()))
        .orElse(null);
  }
}
