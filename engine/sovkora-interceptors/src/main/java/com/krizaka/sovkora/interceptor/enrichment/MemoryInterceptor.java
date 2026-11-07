package com.krizaka.sovkora.interceptor.enrichment;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.ports.outbound.ConversationMemory;
import com.krizaka.sovkora.core.domain.ports.outbound.MemoryResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Conversation memory interceptor — injects historical messages before inference and persists new
 * messages after completion.
 *
 * <p>Uses {@link MemoryResolver} to obtain a per-conversation {@link ConversationMemory} store,
 * enabling multi-turn context within a single conversation thread.
 *
 * <p>A turn in no-storage mode ({@link Context#retentionNone()}) is neither read from nor written
 * to memory.
 *
 * @see MemoryResolver
 */
class MemoryInterceptor implements PromptContextInterceptor {
  private final MemoryResolver memoryResolver;

  MemoryInterceptor(MemoryResolver memoryResolver) {
    this.memoryResolver = Objects.requireNonNull(memoryResolver, "MemoryResolver must not be null");
  }

  @Override
  public EngineChatOptions preProcess(
      InternalChatRequest request,
      String promptText,
      List<EngineMessage> messages,
      EngineChatOptions options) {
    resolveConversationId(request)
        .ifPresent(
            convId -> {
              List<EngineMessage> history = memoryResolver.resolve(convId).get(convId);
              if (!history.isEmpty()) {
                messages.addAll(0, history);
              }
            });
    return options;
  }

  @Override
  public void postProcess(InternalChatRequest request, String promptText, String responseText) {
    resolveConversationId(request)
        .ifPresent(
            convId -> {
              ConversationMemory chatMemory = memoryResolver.resolve(convId);
              List<EngineMessage> newMessages = new ArrayList<>();
              if (promptText != null && !promptText.isBlank()) {
                newMessages.add(EngineMessage.user(promptText));
              }
              newMessages.add(EngineMessage.assistant(responseText));
              chatMemory.add(convId, newMessages);
            });
  }

  /**
   * The conversation to remember in — empty when the turn runs in no-storage mode ({@code
   * retention: none}): nothing is read back, and neither the prompt nor the completion is written.
   */
  private Optional<String> resolveConversationId(InternalChatRequest request) {
    return Optional.ofNullable(request.context())
        .filter(context -> !context.retentionNone())
        .map(Context::conversationId)
        .filter(id -> !id.isBlank());
  }
}
