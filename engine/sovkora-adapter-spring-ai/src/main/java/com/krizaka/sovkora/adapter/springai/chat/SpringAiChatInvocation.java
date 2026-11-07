package com.krizaka.sovkora.adapter.springai.chat;

import com.krizaka.sovkora.adapter.springai.observability.FeatureTagObservationFilter;
import com.krizaka.sovkora.adapter.springai.prompt.SpringPreparedPrompt;
import com.krizaka.sovkora.adapter.springai.tool.ClientToolCallback;
import com.krizaka.sovkora.core.application.routing.DynamicChatModelFactory;
import com.krizaka.sovkora.core.domain.model.chat.CacheStrategy;
import com.krizaka.sovkora.core.domain.model.chat.ChatCompletion;
import com.krizaka.sovkora.core.domain.model.chat.ChatRoute;
import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import com.krizaka.sovkora.core.domain.model.chat.ToolCall;
import com.krizaka.sovkora.core.domain.model.chat.ToolRoundsExceededException;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatInvocation;
import com.krizaka.sovkora.core.domain.ports.outbound.PromptCachePolicy;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolLoopBounds;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import reactor.core.publisher.Flux;

/**
 * {@link ChatInvocation} on Spring AI: the platform's {@link ChatModel}, or a model built for the
 * user's own key. The only place the engines' calls meet the library's model, prompt and response.
 *
 * <p>Spring AI 2 removed the tool loop from the model, so it runs here with the library's {@link
 * ToolCallingManager} (ADR-094 §4.1): the tools the model asks for are run (already resolved and
 * authorised by the tool resolution, bound to the actor), their results go back to the model, until
 * it answers or the bound of rounds is reached. A call to a client tool ({@link
 * ClientToolCallback}) is never run: the turn ends and the calls go back to the client, as before
 * (ADR-093).
 */
public class SpringAiChatInvocation implements ChatInvocation {

  /** Model rounds that may follow a tool call within one turn, unless a bound is given. */
  public static final int DEFAULT_MAX_TOOL_ROUNDS = ToolLoopBounds.DEFAULT_MAX_ROUNDS;

  private final ChatModel platformModel;
  private final DynamicChatModelFactory<ChatModel> modelFactory;
  private final ToolCallingManager toolManager;
  private final ToolLoopBounds bounds;
  private final ObservationRegistry observations;
  private final PromptCachePolicy cachePolicy;

  /**
   * Creates the invocation.
   *
   * @param platformModel the platform's own model
   * @param modelFactory builds a model for a user's key
   */
  public SpringAiChatInvocation(
      ChatModel platformModel, DynamicChatModelFactory<ChatModel> modelFactory) {
    this(
        platformModel, modelFactory, ToolCallingManager.builder().build(), DEFAULT_MAX_TOOL_ROUNDS);
  }

  /**
   * Creates the invocation with its tool loop.
   *
   * @param platformModel the platform's own model
   * @param modelFactory builds a model for a user's key
   * @param toolManager runs the tool calls the model asks for
   * @param maxToolRounds how many tool rounds one turn may take, the same for every feature
   */
  public SpringAiChatInvocation(
      ChatModel platformModel,
      DynamicChatModelFactory<ChatModel> modelFactory,
      ToolCallingManager toolManager,
      int maxToolRounds) {
    this(platformModel, modelFactory, toolManager, ToolLoopBounds.fixed(maxToolRounds));
  }

  /**
   * Creates the invocation with a tool loop bounded per feature key (#169).
   *
   * @param platformModel the platform's own model
   * @param modelFactory builds a model for a user's key
   * @param toolManager runs the tool calls the model asks for
   * @param bounds the most tool rounds a turn of a feature may take (data, edited by an admin)
   */
  public SpringAiChatInvocation(
      ChatModel platformModel,
      DynamicChatModelFactory<ChatModel> modelFactory,
      ToolCallingManager toolManager,
      ToolLoopBounds bounds) {
    this(platformModel, modelFactory, toolManager, bounds, ObservationRegistry.NOOP);
  }

  /**
   * Creates the invocation with its tool loop bounded per feature key, every turn observed under
   * its feature key (#171).
   *
   * @param platformModel the platform's own model
   * @param modelFactory builds a model for a user's key
   * @param toolManager runs the tool calls the model asks for
   * @param bounds the most tool rounds a turn of a feature may take
   * @param observations the host's registry: the turn is opened here so the model's and the tools'
   *     observations wear the feature key
   */
  public SpringAiChatInvocation(
      ChatModel platformModel,
      DynamicChatModelFactory<ChatModel> modelFactory,
      ToolCallingManager toolManager,
      ToolLoopBounds bounds,
      ObservationRegistry observations) {
    this(
        platformModel,
        modelFactory,
        toolManager,
        bounds,
        observations,
        PromptCachePolicy.fixed(PromptCachePolicy.DEFAULT_STRATEGY));
  }

  /**
   * Creates the invocation with the prompt cache decided per feature key (ADR-094 slice 5, #173).
   *
   * @param platformModel the platform's own model
   * @param modelFactory builds a model for a user's key
   * @param toolManager runs the tool calls the model asks for
   * @param bounds the most tool rounds a turn of a feature may take
   * @param observations the host's registry
   * @param cachePolicy what of a turn's prompt a provider may cache (data, edited by an admin); a
   *     SENSITIVE turn is never cached whatever it says
   */
  public SpringAiChatInvocation(
      ChatModel platformModel,
      DynamicChatModelFactory<ChatModel> modelFactory,
      ToolCallingManager toolManager,
      ToolLoopBounds bounds,
      ObservationRegistry observations,
      PromptCachePolicy cachePolicy) {
    this.cachePolicy = Objects.requireNonNull(cachePolicy, "PromptCachePolicy must not be null");
    this.observations =
        Objects.requireNonNull(observations, "ObservationRegistry must not be null");
    this.platformModel = Objects.requireNonNull(platformModel, "ChatModel must not be null");
    this.modelFactory =
        Objects.requireNonNull(modelFactory, "DynamicChatModelFactory must not be null");
    this.toolManager = Objects.requireNonNull(toolManager, "ToolCallingManager must not be null");
    this.bounds = Objects.requireNonNull(bounds, "ToolLoopBounds must not be null");
  }

  @Override
  public ChatCompletion call(PreparedPrompt prompt, ChatRoute route) {
    Observation turn = FeatureTagObservationFilter.openTurn(observations, prompt.featureKey());
    try (Observation.Scope scope = turn.openScope()) {
      return callInTurn(prompt, route);
    } catch (RuntimeException e) {
      turn.error(e);
      throw e;
    } finally {
      turn.stop();
    }
  }

  private ChatCompletion callInTurn(PreparedPrompt prompt, ChatRoute route) {
    ChatModel model = modelOf(prompt, route);
    Prompt current = cachedAsPolicy(model, prompt, promptOf(prompt));
    int maxToolRounds = maxRounds(prompt);
    ChatResponse response = model.call(current);
    TokenUsage used = completion(response, false).usage();
    List<ToolCall> ran = new ArrayList<>();
    for (int round = 0; serverToolsAsked(current, response); round++) {
      if (round >= maxToolRounds) {
        throw new ToolRoundsExceededException(prompt.featureKey(), maxToolRounds);
      }
      ran.addAll(toolCallsOf(response));
      ToolExecutionResult result = toolManager.executeToolCalls(current, response);
      if (result.returnDirect()) {
        response = new ChatResponse(ToolExecutionResult.buildGenerations(result));
        break;
      }
      current = new Prompt(result.conversationHistory(), current.getOptions());
      response = model.call(current);
      used = sum(used, completion(response, false).usage());
    }
    ChatCompletion last = completion(response, false);
    return new ChatCompletion(last.text(), used, last.toolCalls(), ran);
  }

  @Override
  public Flux<ChatCompletion> stream(PreparedPrompt prompt, ChatRoute route) {
    ChatModel model = modelOf(prompt, route);
    Prompt first = cachedAsPolicy(model, prompt, promptOf(prompt));
    int maxToolRounds = maxRounds(prompt);
    return Flux.defer(
        () -> {
          Observation turn =
              FeatureTagObservationFilter.openTurn(observations, prompt.featureKey());
          return streamRound(model, first, 0, prompt.featureKey(), maxToolRounds, turn)
              .contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, turn))
              .doOnError(turn::error)
              .doFinally(signal -> turn.stop());
        });
  }

  /** One streamed model round; when it ended on server tool calls, the next round follows it. */
  private Flux<ChatCompletion> streamRound(
      ChatModel model,
      Prompt current,
      int round,
      String featureKey,
      int maxToolRounds,
      Observation turn) {
    AtomicReference<ChatResponse> asked = new AtomicReference<>();
    return model.stream(current)
        .doOnNext(
            r -> {
              if (serverToolsAsked(current, r)) {
                asked.set(r);
              }
            })
        .map(r -> asked.get() == r ? withoutToolCalls(r) : completion(r, true))
        .concatWith(
            Flux.defer(
                () -> {
                  ChatResponse response = asked.get();
                  if (response == null) {
                    return Flux.empty();
                  }
                  if (round >= maxToolRounds) {
                    return Flux.error(new ToolRoundsExceededException(featureKey, maxToolRounds));
                  }
                  ChatCompletion ran =
                      new ChatCompletion("", TokenUsage.none(), List.of(), toolCallsOf(response));
                  ToolExecutionResult result =
                      turn.scoped(() -> toolManager.executeToolCalls(current, response));
                  if (result.returnDirect()) {
                    return Flux.just(
                        ran,
                        completion(
                            new ChatResponse(ToolExecutionResult.buildGenerations(result)), true));
                  }
                  return Flux.just(ran)
                      .concatWith(
                          streamRound(
                              model,
                              new Prompt(result.conversationHistory(), current.getOptions()),
                              round + 1,
                              featureKey,
                              maxToolRounds,
                              turn));
                }));
  }

  private ChatCompletion withoutToolCalls(ChatResponse response) {
    ChatCompletion all = completion(response, true);
    return new ChatCompletion(all.text(), all.usage(), List.of());
  }

  private int maxRounds(PreparedPrompt prompt) {
    return Math.max(1, bounds.maxRounds(prompt.featureKey()));
  }

  private static TokenUsage sum(TokenUsage a, TokenUsage b) {
    if (a == null) {
      return b;
    }
    return a.plus(b);
  }

  /**
   * True when the model asked for tools and every one is a server tool the engine offered and runs;
   * a client tool, or a name nobody offered, means the whole turn goes back to the caller.
   */
  private static boolean serverToolsAsked(Prompt prompt, ChatResponse response) {
    if (response == null || !response.hasToolCalls()) {
      return false;
    }
    List<org.springframework.ai.tool.ToolCallback> offered =
        prompt.getOptions() instanceof ToolCallingChatOptions tools
            ? tools.getToolCallbacks()
            : List.of();
    return response.getResult().getOutput().getToolCalls().stream()
        .allMatch(
            call ->
                offered.stream()
                    .anyMatch(
                        t ->
                            !(t instanceof ClientToolCallback)
                                && t.getToolDefinition().name().equals(call.name())));
  }

  /**
   * Applies the feature's cache policy to an Anthropic call (the other providers cache a stable
   * prefix by themselves and take no option). A SENSITIVE turn is never cached: its options carry
   * no cache option and the model has no default one, whatever the policy says.
   */
  private Prompt cachedAsPolicy(ChatModel model, PreparedPrompt prepared, Prompt prompt) {
    if (prepared.sensitive() || !(model instanceof AnthropicChatModel)) {
      return prompt;
    }
    CacheStrategy wanted = cachePolicy.strategy(prepared.featureKey());
    if (wanted == null || wanted == CacheStrategy.NONE || prompt.getOptions() == null) {
      return prompt;
    }
    AnthropicCacheStrategy strategy =
        wanted == CacheStrategy.SYSTEM_ONLY
            ? AnthropicCacheStrategy.SYSTEM_ONLY
            : AnthropicCacheStrategy.SYSTEM_AND_TOOLS;
    ChatOptions cached =
        AnthropicChatOptions.builder()
            .combineWith(prompt.getOptions().mutate())
            .cacheOptions(AnthropicCacheOptions.builder().strategy(strategy).build())
            .build();
    return new Prompt(prompt.getInstructions(), cached);
  }

  private ChatModel modelOf(PreparedPrompt prompt, ChatRoute route) {
    if (route == null || route.isPlatform()) {
      return platformModel;
    }
    return modelFactory.createChatModel(route.provider(), prompt.modelName(), route.apiKey());
  }

  private static Prompt promptOf(PreparedPrompt prompt) {
    if (prompt instanceof SpringPreparedPrompt spring) {
      return spring.prompt();
    }
    throw new IllegalArgumentException(
        "Not a Spring AI prompt: " + (prompt == null ? null : prompt.getClass().getName()));
  }

  /** The calls the model asked for; the engine returns them to the client, it never runs them. */
  private static List<ToolCall> toolCallsOf(ChatResponse response) {
    if (response == null
        || response.getResult() == null
        || response.getResult().getOutput() == null) {
      return List.of();
    }
    return response.getResult().getOutput().getToolCalls().stream()
        .map(call -> new ToolCall(call.id(), call.name(), call.arguments()))
        .toList();
  }

  /** Reads text and usage; a chunk without text is empty rather than absent when streaming. */
  private static ChatCompletion completion(ChatResponse response, boolean emptyIfAbsent) {
    String text = null;
    if (response != null
        && response.getResult() != null
        && response.getResult().getOutput() != null) {
      text = response.getResult().getOutput().getText();
    }
    if (text == null && emptyIfAbsent) {
      text = "";
    }
    TokenUsage usage = TokenUsage.none();
    if (response != null && response.getMetadata() != null) {
      var reported = response.getMetadata().getUsage();
      if (reported != null) {
        usage =
            TokenUsage.reported(
                reported.getPromptTokens(),
                reported.getCompletionTokens(),
                reported.getTotalTokens(),
                reported.getCacheReadInputTokens(),
                reported.getCacheWriteInputTokens());
      }
    }
    return new ChatCompletion(text, usage, toolCallsOf(response));
  }
}
