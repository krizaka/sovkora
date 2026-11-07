package com.krizaka.sovkora.adapter.springai.prompt;

import com.krizaka.sovkora.adapter.springai.tool.ClientToolCallback;
import com.krizaka.sovkora.core.application.routing.ProviderClassifier;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import com.krizaka.sovkora.core.domain.model.chat.PromptDraft;
import com.krizaka.sovkora.core.domain.ports.outbound.PromptAssembly;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/** {@link PromptAssembly} on Spring AI: messages, provider options and tool callbacks. */
public final class SpringAiPromptAssembly implements PromptAssembly {

  @Override
  public String providerOf(EngineChatOptions options) {
    return SpringAiChatOptionsInfo.providerOf(MessageCompiler.toSpring(options));
  }

  @Override
  public PromptDraft draft(
      InternalChatRequest request,
      String promptText,
      Optional<byte[]> image,
      String provider,
      String ollamaChatModel) {
    List<Message> messages = MessageCompiler.compile(request, promptText, image);
    ChatOptions options = MessageCompiler.toSpring(request.options());
    if (ProviderClassifier.ollama().equalsIgnoreCase(provider)) {
      options = ChatOptionsResolver.resolveOllamaOptions(options, ollamaChatModel);
    } else if (options == null) {
      options = ChatOptionsResolver.resolveDefaultOptions(provider);
    }
    return new PromptDraft(MessageCompiler.toEngine(messages), MessageCompiler.toEngine(options));
  }

  @Override
  public PreparedPrompt assemble(
      List<EngineMessage> messages, EngineChatOptions options, String provider, List<?> tools) {
    ChatOptions finalOptions = MessageCompiler.toSpring(options);
    // The client's tools (ADR-093 §4) are offered to every provider, the local one included: that
    // is the sovereign install's whole point. The engine's own tools keep their rule.
    boolean clientTools =
        !tools.isEmpty() && tools.stream().allMatch(ClientToolCallback.class::isInstance);
    if (!tools.isEmpty()
        && finalOptions instanceof ToolCallingChatOptions toolCalling
        && (clientTools || !ProviderClassifier.ollama().equalsIgnoreCase(provider))) {
      List<ToolCallback> callbacks = tools.stream().map(ToolCallback.class::cast).toList();
      // Spring AI 2.0 options are immutable: rebuild via mutate() rather than a setter.
      finalOptions = toolCalling.mutate().toolCallbacks(callbacks).build();
    }
    return new SpringPreparedPrompt(new Prompt(MessageCompiler.toSpring(messages), finalOptions));
  }
}
