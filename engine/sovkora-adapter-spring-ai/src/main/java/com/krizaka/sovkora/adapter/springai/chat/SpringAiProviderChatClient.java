package com.krizaka.sovkora.adapter.springai.chat;

import com.krizaka.sovkora.adapter.springai.prompt.PipelineOptionsRegistry;
import com.krizaka.sovkora.adapter.springai.response.SpringAiResponses;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import java.util.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Spring AI implementation of {@link ProviderChatClient}: the models are the provider-keyed {@link
 * ChatModel} beans, the options come from the {@link PipelineOptionsRegistry}.
 */
public class SpringAiProviderChatClient implements ProviderChatClient {

  private final Map<String, ChatModel> chatModels;
  private final PipelineOptionsRegistry optionsRegistry;

  /**
   * Creates the client.
   *
   * @param chatModels the models by provider key (may be {@code null})
   * @param optionsRegistry builds the provider-specific options
   */
  public SpringAiProviderChatClient(
      Map<String, ChatModel> chatModels, PipelineOptionsRegistry optionsRegistry) {
    // Registration order is kept: the first model is the default one.
    this.chatModels =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNullElse(chatModels, Map.of())));
    this.optionsRegistry = Objects.requireNonNull(optionsRegistry, "optionsRegistry");
  }

  @Override
  public Set<String> providers() {
    return chatModels.keySet();
  }

  @Override
  public Optional<String> resolve(String requested) {
    if (chatModels.containsKey(requested)) {
      return Optional.of(requested);
    }
    return chatModels.keySet().stream()
        .filter(key -> key.toLowerCase().contains(requested.toLowerCase()))
        .findFirst();
  }

  @Override
  public String complete(
      String provider,
      String optionsProvider,
      List<EngineMessage> messages,
      String model,
      Double temperature) {
    ChatModel chatModel = modelOf(provider);
    ChatOptions options = optionsRegistry.build(optionsProvider, model, temperature);
    return SpringAiResponses.extractRawText(
        chatModel.call(new Prompt(toSpring(messages), options)));
  }

  @Override
  public String completeWithDefault(List<EngineMessage> messages, Double temperature) {
    ChatModel chatModel =
        chatModels.values().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No chat model is registered."));
    Prompt prompt =
        temperature == null
            ? new Prompt(toSpring(messages))
            : new Prompt(
                toSpring(messages), ChatOptions.builder().temperature(temperature).build());
    return SpringAiResponses.extractRawText(chatModel.call(prompt));
  }

  private ChatModel modelOf(String provider) {
    ChatModel chatModel = chatModels.get(provider);
    if (chatModel == null) {
      throw new IllegalArgumentException("Provider '" + provider + "' is not registered.");
    }
    return chatModel;
  }

  private static List<Message> toSpring(List<EngineMessage> messages) {
    return messages.stream().map(SpringAiProviderChatClient::toSpring).toList();
  }

  private static Message toSpring(EngineMessage message) {
    return switch (message.role()) {
      case SYSTEM -> new SystemMessage(message.text());
      case USER -> new UserMessage(message.text());
      case ASSISTANT -> new AssistantMessage(message.text());
      case TOOL ->
          throw new IllegalArgumentException("A tool answer cannot be sent as a plain message.");
    };
  }
}
