package com.krizaka.sovkora.adapter.springai.prompt;

import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

class MessageCompilerTest {

  @Test
  void mapMessage_systemRole_returnsSystemMessage() {
    var msg = new InternalChatRequest.ChatMessage("system", "System instruction");
    Message result = MessageCompiler.mapMessage(msg);
    assertInstanceOf(SystemMessage.class, result);
  }

  @Test
  void mapMessage_assistantRole_returnsAssistantMessage() {
    var msg = new InternalChatRequest.ChatMessage("assistant", "AI response");
    Message result = MessageCompiler.mapMessage(msg);
    assertInstanceOf(AssistantMessage.class, result);
  }

  @Test
  void mapMessage_userRole_returnsUserMessage() {
    var msg = new InternalChatRequest.ChatMessage("user", "User query");
    Message result = MessageCompiler.mapMessage(msg);
    assertInstanceOf(UserMessage.class, result);
  }

  @Test
  void mapMessage_unknownRole_defaultsToUserMessage() {
    var msg = new InternalChatRequest.ChatMessage("unknown", "Content");
    Message result = MessageCompiler.mapMessage(msg);
    assertInstanceOf(UserMessage.class, result);
  }

  @Test
  void mapMessage_upperCaseRole_handlesCaseInsensitive() {
    var msg = new InternalChatRequest.ChatMessage("SYSTEM", "Sys msg");
    Message result = MessageCompiler.mapMessage(msg);
    assertInstanceOf(SystemMessage.class, result);
  }

  @Test
  void compile_emptyMessages_withTextPrompt_appendsUserMessage() {
    var request = new InternalChatRequest("hello", List.of(), null, Context.anonymous());

    List<Message> result = MessageCompiler.compile(request, "hello", java.util.Optional.empty());

    assertEquals(1, result.size());
    assertInstanceOf(UserMessage.class, result.getFirst());
  }

  @Test
  void compile_textTurn_neverLiftsMediaOutOfTheResolvedPrompt() {
    // ADR-063: the resolved prompt is the pipeline's output. A marker in it is text — the pipeline
    // was told this turn carried no media, so an image lifted out here would bypass every guard.
    var request = new InternalChatRequest("fuzzy", List.of(), null, Context.anonymous());

    List<Message> result =
        MessageCompiler.compile(
            request, "refined [posterBase64: dGVzdA==]", java.util.Optional.empty());

    assertEquals(1, result.size());
    assertTrue(((UserMessage) result.getFirst()).getMedia().isEmpty());
    assertEquals("refined [posterBase64: dGVzdA==]", result.getFirst().getText());
  }

  @Test
  void compile_blankPrompt_returnsOnlyHistory() {
    var msg = new InternalChatRequest.ChatMessage("user", "old msg");
    var request = new InternalChatRequest("", List.of(msg), null, Context.anonymous());

    List<Message> result = MessageCompiler.compile(request, "", java.util.Optional.empty());

    assertEquals(1, result.size());
  }

  @Test
  void compile_withHistory_preservesOrder() {
    var sys = new InternalChatRequest.ChatMessage("system", "system message");
    var user = new InternalChatRequest.ChatMessage("user", "user query");
    var assistant = new InternalChatRequest.ChatMessage("assistant", "ai response");
    var request =
        new InternalChatRequest(
            "new prompt", List.of(sys, user, assistant), null, Context.anonymous());

    List<Message> result =
        MessageCompiler.compile(request, "new prompt", java.util.Optional.empty());

    assertEquals(4, result.size());
    assertInstanceOf(SystemMessage.class, result.getFirst());
    assertInstanceOf(UserMessage.class, result.get(1));
    assertInstanceOf(AssistantMessage.class, result.get(2));
    assertInstanceOf(UserMessage.class, result.get(3));
  }

  @Test
  void toSpring_untouchedEngineOptions_returnsTheOriginalObject() {
    ChatOptions original = ChatOptions.builder().model("a").temperature(0.2).maxTokens(100).build();

    assertSame(original, MessageCompiler.toSpring(MessageCompiler.toEngine(original)));
  }

  @Test
  void toSpring_editedSettings_reachTheNativeOptionsAndProviderSettingsSurvive() {
    ToolCallingChatOptions original =
        ToolCallingChatOptions.builder()
            .model("a")
            .temperature(0.2)
            .maxTokens(100)
            .toolContext("tenant", "t-1")
            .build();
    EngineChatOptions engine = MessageCompiler.toEngine(original);

    EngineChatOptions edited = new EngineChatOptions("b", 0.9, 50, engine.nativeOptions());
    ChatOptions result = MessageCompiler.toSpring(edited);

    assertEquals("b", result.getModel());
    assertEquals(0.9, result.getTemperature());
    assertEquals(50, result.getMaxTokens());
    assertInstanceOf(ToolCallingChatOptions.class, result);
    assertEquals("t-1", ((ToolCallingChatOptions) result).getToolContext().get("tenant"));
    // the options the interceptor was handed are not mutated behind its back
    assertEquals("a", original.getModel());
  }

  @Test
  void toSpring_noNativeOptions_buildsFromTheSettings() {
    ChatOptions result = MessageCompiler.toSpring(new EngineChatOptions("m", 0.5, 10));

    assertEquals("m", result.getModel());
    assertEquals(0.5, result.getTemperature());
    assertEquals(10, result.getMaxTokens());
  }

  @Test
  void toSpring_nullStaysNull() {
    assertNull(MessageCompiler.toSpring((EngineChatOptions) null));
  }
}
