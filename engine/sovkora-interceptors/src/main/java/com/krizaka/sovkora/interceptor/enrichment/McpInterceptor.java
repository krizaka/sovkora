package com.krizaka.sovkora.interceptor.enrichment;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.ports.outbound.McpOrchestrator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MCP (Model Context Protocol) interceptor — puts the external context resolved via the {@link
 * McpOrchestrator} in front of the model <b>as data</b>.
 *
 * <p>It used to prepend {@code SystemMessage("MCP Context: …")}: the raw body of a platform or
 * user-declared server became a system instruction (sovkora-engine#57). Now the content goes in a
 * user-role {@link ExternalContent} block just before the user's turn, and the only system text
 * added is our own constant rule that a block is not instructions.
 *
 * @see McpOrchestrator#resolveExternalContext()
 * @see ExternalContent
 */
class McpInterceptor implements PromptContextInterceptor {
  private final McpOrchestrator mcpOrchestrator;

  McpInterceptor(McpOrchestrator mcpOrchestrator) {
    this.mcpOrchestrator =
        Objects.requireNonNull(mcpOrchestrator, "McpOrchestrator must not be null");
  }

  @Override
  public EngineChatOptions preProcess(
      InternalChatRequest request,
      String promptText,
      List<EngineMessage> messages,
      EngineChatOptions options) {
    Optional.ofNullable(mcpOrchestrator.resolveExternalContext())
        .filter(ctx -> !ctx.isBlank())
        .ifPresent(ctx -> ExternalContent.insert(messages, "MCP servers", ctx));
    return options;
  }
}
