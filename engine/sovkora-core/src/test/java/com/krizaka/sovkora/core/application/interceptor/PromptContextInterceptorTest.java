package com.krizaka.sovkora.core.application.interceptor;

import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PromptContextInterceptorTest {

  @Test
  @DisplayName("default interface methods behave correctly")
  void defaultInterfaceMethods() {
    PromptContextInterceptor interceptor = new PromptContextInterceptor() {};
    // getId() defaults to simple class name
    assertNotNull(interceptor.getId());
    assertEquals(false, interceptor.isAiDependent());

    PromptContext context = new PromptContext("test query", Map.of());
    assertSame(context, interceptor.beforeExecution(context));

    InternalChatRequest request = Mockito.mock(InternalChatRequest.class);
    EngineChatOptions options = new EngineChatOptions(null, null, null);
    assertSame(options, interceptor.preProcess(request, "prompt", new ArrayList<>(), options));

    // Calling postProcess should not throw any exception
    interceptor.postProcess(request, "prompt", "response");
  }
}
