package com.krizaka.sovkora.interceptor.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.application.pipeline.PipelineRegistry;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserContextInterceptorTest {

  private static final String KEY = "UserContextInterceptor";

  private PipelineRegistry registryReturning(boolean enabled) {
    PipelineRegistry registry = mock(PipelineRegistry.class);
    when(registry.isInterceptorEnabled(KEY)).thenReturn(enabled);
    return registry;
  }

  @Test
  @DisplayName("preProcess does nothing when the interceptor is disabled in the DB registry")
  void disabledUserContext() {
    UserContextInterceptor interceptor = new UserContextInterceptor(registryReturning(false));
    InternalChatRequest request = mock(InternalChatRequest.class);
    List<EngineMessage> messages = new ArrayList<>();
    EngineChatOptions options = new EngineChatOptions(null, null, null);

    EngineChatOptions result = interceptor.preProcess(request, "prompt", messages, options);
    assertThat(result).isSameAs(options);
    assertThat(messages).isEmpty();
  }

  @Test
  @DisplayName("preProcess injects constraints from preferences (snake_case)")
  void injectsConstraintsSnakeCase() {
    UserContextInterceptor interceptor = new UserContextInterceptor(registryReturning(true));

    InternalChatRequest request = mock(InternalChatRequest.class);
    Context context = mock(Context.class);
    Map<String, Object> preferences =
        Map.of(
            "preference.primary_industry", "Finance",
            "preference.ai_behavior", "Helpful assistant");
    when(request.context()).thenReturn(context);
    when(context.preferences()).thenReturn(preferences);

    List<EngineMessage> messages = new ArrayList<>();
    EngineChatOptions options = new EngineChatOptions(null, null, null);

    interceptor.preProcess(request, "prompt", messages, options);

    assertThat(messages).hasSize(1);
    assertThat(messages.getFirst().text())
        .contains("System constraints:")
        .contains("User Primary Industry: Finance")
        .contains("User AI Behavior: Helpful assistant");
  }

  @Test
  @DisplayName("preProcess injects constraints from preferences (camelCase)")
  void injectsConstraintsCamelCase() {
    UserContextInterceptor interceptor = new UserContextInterceptor(registryReturning(true));

    InternalChatRequest request = mock(InternalChatRequest.class);
    Context context = mock(Context.class);
    Map<String, Object> preferences =
        Map.of(
            "preference.primaryIndustry", "Healthcare",
            "preference.aiBehavior", "Professional advisor");
    when(request.context()).thenReturn(context);
    when(context.preferences()).thenReturn(preferences);

    List<EngineMessage> messages = new ArrayList<>();
    EngineChatOptions options = new EngineChatOptions(null, null, null);

    interceptor.preProcess(request, "prompt", messages, options);

    assertThat(messages).hasSize(1);
    assertThat(messages.getFirst().text())
        .contains("System constraints:")
        .contains("User Primary Industry: Healthcare")
        .contains("User AI Behavior: Professional advisor");
  }

  @Test
  @DisplayName("preProcess handles null or empty preference values gracefully")
  void emptyPreferencesGraceful() {
    UserContextInterceptor interceptor = new UserContextInterceptor(registryReturning(true));

    InternalChatRequest request = mock(InternalChatRequest.class);
    Context context = mock(Context.class);
    Map<String, Object> preferences = new HashMap<>();
    preferences.put("preference.primary_industry", "");
    preferences.put("preference.ai_behavior", null);
    when(request.context()).thenReturn(context);
    when(context.preferences()).thenReturn(preferences);

    List<EngineMessage> messages = new ArrayList<>();
    EngineChatOptions options = new EngineChatOptions(null, null, null);

    interceptor.preProcess(request, "prompt", messages, options);

    assertThat(messages).isEmpty();
  }

  @Test
  @DisplayName("preProcess handles exceptions gracefully without propagating")
  void exceptionSafety() {
    UserContextInterceptor interceptor = new UserContextInterceptor(registryReturning(true));

    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.context()).thenThrow(new RuntimeException("Simulated Database / Context failure"));

    List<EngineMessage> messages = new ArrayList<>();
    EngineChatOptions options = new EngineChatOptions(null, null, null);

    assertThatCode(() -> interceptor.preProcess(request, "prompt", messages, options))
        .doesNotThrowAnyException();
    assertThat(messages).isEmpty();
  }
}
