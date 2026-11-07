package com.krizaka.sovkora.interceptor.tooling;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolOptions;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool execution interceptor — analyzes prompt content to selectively attach relevant {@link
 * EngineTool} definitions to the chat options.
 *
 * <p>Uses keyword-based demand detection to determine which tools are relevant for the current
 * prompt (e.g., image analysis for "poster", audio analysis for "audio").
 *
 * @see ToolRegistry
 */
class ToolInterceptor implements PromptContextInterceptor {
  private final ToolRegistry toolRegistry;
  private final ToolOptions toolOptions;

  ToolInterceptor(ToolRegistry toolRegistry, ToolOptions toolOptions) {
    this.toolRegistry = toolRegistry;
    this.toolOptions = toolOptions;
  }

  @Override
  public EngineChatOptions preProcess(
      InternalChatRequest request,
      String promptText,
      List<EngineMessage> messages,
      EngineChatOptions options) {
    // Sync and streamed turns offer the same tools: the invocation port runs the loop in both
    // modes (ADR-094 §4.1, sovkora-engine#169).
    // In client-executed mode the client's tools are the closed list (ADR-093 §4): the engine's own
    // are not offered beside them.
    if (request.clientToolMode()) {
      return options;
    }
    if (options != null && options.model() != null) {
      String modelLower = options.model().toLowerCase();
      if (modelLower.contains("vision")
          || modelLower.contains("llava")
          || modelLower.contains("bakllava")) {
        return options;
      }
    }
    if (toolRegistry == null) {
      return options;
    }
    // The tools of this turn are the ones its actor may use; no actor, no tools (ADR-091).
    Actor actor = Actor.from(request.context()).orElse(null);
    List<EngineTool> permitted = toolRegistry.getRegisteredTools(actor);
    if (permitted.isEmpty()) {
      return options;
    }

    List<EngineTool> demandedTools = collectDemandedTools(permitted, request, promptText);
    if (demandedTools.isEmpty()) {
      return options;
    }

    return toolOptions.withTools(options, demandedTools);
  }

  private List<EngineTool> collectDemandedTools(
      List<EngineTool> permitted, InternalChatRequest request, String promptText) {
    String lowerContext = extractFullText(request, promptText).toLowerCase();
    List<EngineTool> demanded = new ArrayList<>();

    for (EngineTool tool : permitted) {
      String name = tool.name();
      boolean isSpecialized = isSpecializedTool(name);
      if (!isSpecialized || matchesDemand(name, lowerContext)) {
        demanded.add(tool);
      }
    }
    return demanded;
  }

  private boolean isSpecializedTool(String name) {
    return "analyzePoster".equals(name) || "analyzeAudioExtract".equals(name);
  }

  private boolean matchesDemand(String toolName, String lowerContext) {
    return ("analyzePoster".equals(toolName) && demandsPosterTool(lowerContext))
        || ("analyzeAudioExtract".equals(toolName) && demandsAudioTool(lowerContext));
  }

  private String extractFullText(InternalChatRequest request, String promptText) {
    StringBuilder text = new StringBuilder(promptText != null ? promptText : "");
    text.append(" ").append(request.prompt() != null ? request.prompt() : "");
    if (request.messages() != null) {
      request.messages().stream()
          .filter(m -> m.content() != null)
          .forEach(m -> text.append(" ").append(m.content()));
    }
    return text.toString();
  }

  private boolean demandsPosterTool(String lowerContext) {
    return lowerContext.contains("poster")
        || lowerContext.contains("image")
        || lowerContext.contains("visual")
        || lowerContext.contains("picture");
  }

  private boolean demandsAudioTool(String lowerContext) {
    return lowerContext.contains("audio")
        || lowerContext.contains("clip")
        || lowerContext.contains("voice")
        || lowerContext.contains("music")
        || lowerContext.contains("sound");
  }
}
