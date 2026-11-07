package com.krizaka.sovkora.core.application;

import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import com.krizaka.sovkora.core.domain.model.chat.PromptDraft;
import com.krizaka.sovkora.core.domain.ports.outbound.PromptAssembly;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/** A plain {@link PromptAssembly} for the core's tests: user messages, no provider options. */
public final class TestPromptAssembly implements PromptAssembly {

  @Override
  public String providerOf(EngineChatOptions options) {
    return null;
  }

  @Override
  public PromptDraft draft(
      InternalChatRequest request,
      String promptText,
      Optional<byte[]> image,
      String provider,
      String ollamaChatModel) {
    List<EngineMessage> messages = new ArrayList<>();
    if (request.messages() != null) {
      for (InternalChatRequest.ChatMessage m : request.messages()) {
        messages.add(new EngineMessage(role(m.role()), m.content(), null));
      }
    }
    if (promptText != null && !promptText.isBlank()) {
      messages.add(new EngineMessage(EngineMessage.Role.USER, promptText, null));
    }
    EngineChatOptions options = request.options();
    if ("ollama".equalsIgnoreCase(provider) && options == null) {
      options =
          new EngineChatOptions(
              ollamaChatModel,
              0.7,
              null,
              org.springframework.ai.ollama.api.OllamaChatOptions.builder()
                  .model(ollamaChatModel)
                  .temperature(0.7)
                  .build());
    }
    return new PromptDraft(messages, options);
  }

  @Override
  public PreparedPrompt assemble(
      List<EngineMessage> messages, EngineChatOptions options, String provider, List<?> tools) {
    ChatOptions base =
        options != null && options.nativeOptions() instanceof ChatOptions own
            ? own
            : ToolCallingChatOptions.builder()
                .model(options == null ? null : options.model())
                .temperature(options == null ? null : options.temperature())
                .build();
    if (!tools.isEmpty()
        && !"ollama".equalsIgnoreCase(provider)
        && base instanceof ToolCallingChatOptions toolCalling) {
      base =
          toolCalling
              .mutate()
              .toolCallbacks(tools.stream().map(ToolCallback.class::cast).toList())
              .build();
    }
    List<Message> spring = new ArrayList<>();
    for (EngineMessage m : messages) {
      spring.add(
          switch (m.role()) {
            case SYSTEM -> new SystemMessage(m.text());
            case ASSISTANT -> new AssistantMessage(m.text());
            default -> new UserMessage(m.text());
          });
    }
    return new SpringPreparedPrompt(new Prompt(spring, base));
  }

  private static EngineMessage.Role role(String role) {
    return switch (role.toLowerCase()) {
      case "system" -> EngineMessage.Role.SYSTEM;
      case "assistant" -> EngineMessage.Role.ASSISTANT;
      default -> EngineMessage.Role.USER;
    };
  }
}
