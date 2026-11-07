package com.krizaka.sovkora.adapter.springai.tool;

import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolOptions;
import java.util.List;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Spring AI implementation of the {@link ToolOptions} port: the provider's own options carry the
 * tools (Ollama, OpenAI); any other options are returned as they came.
 */
public class SpringAiToolOptions implements ToolOptions {

  private static final int OLLAMA_CONTEXT_WINDOW = 8192;

  @Override
  public EngineChatOptions withTools(EngineChatOptions options, List<EngineTool> tools) {
    if (options == null) {
      return null;
    }
    List<ToolCallback> callbacks = tools.stream().map(SpringAiToolOptions::callbackOf).toList();
    // Spring AI 2.0 dropped builder .toolNames(Set) — tool registration is via .toolCallbacks()
    // alone.
    return switch (options.nativeOptions()) {
      case OllamaChatOptions ollama ->
          options.withNativeOptions(
              OllamaChatOptions.builder()
                  .model(ollama.getModel())
                  .temperature(ollama.getTemperature())
                  .numPredict(ollama.getNumPredict())
                  .numCtx(OLLAMA_CONTEXT_WINDOW)
                  .toolCallbacks(callbacks)
                  .build());
      case OpenAiChatOptions openai ->
          options.withNativeOptions(
              OpenAiChatOptions.builder()
                  .model(openai.getModel())
                  .temperature(openai.getTemperature())
                  .maxTokens(openai.getMaxTokens())
                  .toolCallbacks(callbacks)
                  .build());
      case null, default -> options;
    };
  }

  private static ToolCallback callbackOf(EngineTool tool) {
    if (tool.nativeTool() instanceof ToolCallback original) {
      return original;
    }
    throw new IllegalArgumentException("Tool not registered through Spring AI: " + tool.name());
  }
}
