package com.krizaka.sovkora.interceptor.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.Authority;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.KnowledgeService;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RagInterceptorTest {

  private static final Context EMPLOYEE_CONTEXT =
      new Context("emp-1", "c-1", Map.of(), Set.of(new Authority("ROLE_USER")));
  private static final Actor EMPLOYEE = Actor.from(EMPLOYEE_CONTEXT).orElseThrow();

  @Test
  @DisplayName("preProcess injects RAG context if enabled and context is not empty")
  void preProcessInjectsRagContext() {
    CoreProperties props = mock(CoreProperties.class);
    CoreProperties.RagConfig ragConfig = mock(CoreProperties.RagConfig.class);
    when(props.rag()).thenReturn(ragConfig);
    when(ragConfig.enabled()).thenReturn(true);
    when(ragConfig.topK()).thenReturn(5);

    KnowledgeService knowledgeService = mock(KnowledgeService.class);
    when(knowledgeService.retrieveContext(EMPLOYEE, "test query", 5))
        .thenReturn("Relevant knowledge chunks");

    RagInterceptor interceptor = new RagInterceptor(props, knowledgeService);
    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.context()).thenReturn(EMPLOYEE_CONTEXT);
    List<EngineMessage> messages = new ArrayList<>();
    EngineChatOptions options = new EngineChatOptions(null, null, null);

    interceptor.preProcess(request, "test query", messages, options);

    // The chunks are data in a user-role block, never a system instruction (#57).
    assertThat(messages)
        .filteredOn(m -> m.role() == EngineMessage.Role.SYSTEM)
        .noneMatch(m -> m.text().contains("Relevant knowledge chunks"));
    assertThat(messages)
        .filteredOn(m -> m.role() == EngineMessage.Role.USER)
        .anyMatch(
            m ->
                m.text().startsWith(ExternalContent.OPEN)
                    && m.text().contains("Relevant knowledge chunks"));
  }

  @Test
  @DisplayName("preProcess does nothing if RAG config is disabled")
  void preProcessRagDisabled() {
    CoreProperties props = mock(CoreProperties.class);
    CoreProperties.RagConfig ragConfig = mock(CoreProperties.RagConfig.class);
    when(props.rag()).thenReturn(ragConfig);
    when(ragConfig.enabled()).thenReturn(false);

    KnowledgeService knowledgeService = mock(KnowledgeService.class);

    RagInterceptor interceptor = new RagInterceptor(props, knowledgeService);
    InternalChatRequest request = mock(InternalChatRequest.class);
    List<EngineMessage> messages = new ArrayList<>();
    EngineChatOptions options = new EngineChatOptions(null, null, null);

    interceptor.preProcess(request, "test query", messages, options);

    assertThat(messages).isEmpty();
    verifyNoInteractions(knowledgeService);
  }

  @Test
  @DisplayName("a turn with no verified actor retrieves nothing — never everything (ADR-091)")
  void preProcessWithoutAnActorRetrievesNothing() {
    CoreProperties props = mock(CoreProperties.class);
    CoreProperties.RagConfig ragConfig = mock(CoreProperties.RagConfig.class);
    when(props.rag()).thenReturn(ragConfig);
    when(ragConfig.enabled()).thenReturn(true);

    KnowledgeService knowledgeService = mock(KnowledgeService.class);
    RagInterceptor interceptor = new RagInterceptor(props, knowledgeService);

    for (Context anonymous : java.util.Arrays.asList(Context.anonymous(), null)) {
      InternalChatRequest request = mock(InternalChatRequest.class);
      when(request.context()).thenReturn(anonymous);
      List<EngineMessage> messages = new ArrayList<>();

      interceptor.preProcess(
          request, "le salaire du CEO", messages, new EngineChatOptions(null, null, null));

      assertThat(messages).isEmpty();
    }
    verifyNoInteractions(knowledgeService);
  }
}
