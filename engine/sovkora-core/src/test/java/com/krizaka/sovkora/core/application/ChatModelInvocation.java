package com.krizaka.sovkora.core.application;

import com.krizaka.sovkora.core.application.routing.DynamicChatModelFactory;
import com.krizaka.sovkora.core.domain.model.chat.ChatCompletion;
import com.krizaka.sovkora.core.domain.model.chat.ChatRoute;
import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatInvocation;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Test double of the port over a mocked {@link ChatModel}, so the engines' tests keep scripting the
 * model. The real implementation, and its tests, are in {@code sovkora-adapter-spring-ai}.
 */
public final class ChatModelInvocation implements ChatInvocation {

  private final ChatModel platform;
  private final DynamicChatModelFactory<ChatModel> factory;

  private ChatModelInvocation(ChatModel platform, DynamicChatModelFactory<ChatModel> factory) {
    this.platform = platform;
    this.factory = factory;
  }

  public static ChatInvocation of(ChatModel platform, DynamicChatModelFactory<ChatModel> factory) {
    return new ChatModelInvocation(platform, factory);
  }

  /** The Spring prompt behind a prepared one, for assertions on what was compiled. */
  public static Prompt spring(PreparedPrompt prompt) {
    return prompt == null ? null : ((SpringPreparedPrompt) prompt).prompt();
  }

  private ChatModel model(PreparedPrompt prompt, ChatRoute route) {
    return route.isPlatform()
        ? platform
        : factory.createChatModel(route.provider(), prompt.modelName(), route.apiKey());
  }

  @Override
  public ChatCompletion call(PreparedPrompt prompt, ChatRoute route) {
    return completion(model(prompt, route).call(spring(prompt)));
  }

  @Override
  public Flux<ChatCompletion> stream(PreparedPrompt prompt, ChatRoute route) {
    return model(prompt, route).stream(spring(prompt)).map(ChatModelInvocation::completion);
  }

  private static ChatCompletion completion(ChatResponse r) {
    if (r == null) {
      return null;
    }
    String text =
        r.getResult() == null || r.getResult().getOutput() == null
            ? null
            : r.getResult().getOutput().getText();
    var u = r.getMetadata() == null ? null : r.getMetadata().getUsage();
    TokenUsage usage =
        u == null
            ? TokenUsage.none()
            : TokenUsage.reported(u.getPromptTokens(), u.getCompletionTokens(), u.getTotalTokens());
    return new ChatCompletion(text, usage);
  }
}
