package com.krizaka.sovkora.business.usecases.chat;

import com.krizaka.sovkora.business.api.*;
import com.krizaka.sovkora.business.prompt.MarkdownPromptResolver;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.ChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.ChatRequest.ChatMessage;
import com.krizaka.sovkora.core.domain.model.chat.ChatResponse;
import com.krizaka.sovkora.core.domain.ports.inbound.AiClient;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reference use-case — a synchronous chat assistant. Demonstrates the App Factory contract: it
 * declares a {@link UseCaseDescriptor} (with a markdown {@code persona}), resolves the persona via
 * {@link MarkdownPromptResolver}, and orchestrates the core's {@link AiClient} inbound port. A new
 * product capability is added the same way — a new {@code UseCase} class — without touching the
 * core or the router.
 */
public final class ChatAssistantUseCase implements UseCase<ChatPayload, ChatResponse> {

  private static final UseCaseDescriptor DESCRIPTOR =
      new UseCaseDescriptor(
          "chat.assistant",
          Capability.CHAT,
          Set.of("personas/default-assistant"),
          PlanningMode.DETERMINISTIC,
          Set.of(),
          RbacPolicy.PERMIT_ALL);

  private final AiClient aiClient;
  private final MarkdownPromptResolver prompts;

  public ChatAssistantUseCase(AiClient aiClient, MarkdownPromptResolver prompts) {
    this.aiClient = Objects.requireNonNull(aiClient, "aiClient must not be null");
    this.prompts = Objects.requireNonNull(prompts, "prompts must not be null");
  }

  @Override
  public UseCaseDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public ChatResponse execute(UseCaseContext ctx, ChatPayload input) {
    Context coreContext =
        new Context(
            ctx.actorId() != null ? ctx.actorId() : "anonymous",
            ctx.sessionId() != null ? ctx.sessionId() : ctx.intentionId(),
            ctx.preferences(),
            Set.of());
    return aiClient.chat(new ChatRequest(input.prompt(), personaMessages(), Map.of(), coreContext));
  }

  /** Resolves each declared persona's markdown into a leading system message. */
  private List<ChatMessage> personaMessages() {
    return descriptor().personas().stream()
        .map(prompts::resolve)
        .flatMap(java.util.Optional::stream)
        .filter(content -> !content.isBlank())
        .map(content -> new ChatMessage("system", content))
        .toList();
  }
}
