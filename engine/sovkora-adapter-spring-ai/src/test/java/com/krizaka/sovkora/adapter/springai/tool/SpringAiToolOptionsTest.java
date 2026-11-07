package com.krizaka.sovkora.adapter.springai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

class SpringAiToolOptionsTest {

  private final SpringAiToolOptions toolOptions = new SpringAiToolOptions();
  private final ToolCallback first = mock(ToolCallback.class);
  private final ToolCallback second = mock(ToolCallback.class);

  private static EngineTool engineTool(String name, ToolCallback callback) {
    return new EngineTool(name, "", "{}", callback::call, callback);
  }

  private static EngineChatOptions wrap(ChatOptions options) {
    return new EngineChatOptions(
        options.getModel(), options.getTemperature(), options.getMaxTokens(), options);
  }

  @Test
  @DisplayName("Ollama options keep their settings and carry the tools")
  void ollamaCarriesTheTools() {
    OllamaChatOptions options =
        OllamaChatOptions.builder().model("llama3.2").temperature(0.7).numPredict(128).build();

    EngineChatOptions result =
        toolOptions.withTools(
            wrap(options), List.of(engineTool("a", first), engineTool("b", second)));

    assertThat(result.nativeOptions()).isInstanceOf(OllamaChatOptions.class);
    OllamaChatOptions ollama = (OllamaChatOptions) result.nativeOptions();
    assertThat(ollama.getModel()).isEqualTo("llama3.2");
    assertThat(ollama.getTemperature()).isEqualTo(0.7);
    assertThat(ollama.getNumPredict()).isEqualTo(128);
    assertThat(ollama.getToolCallbacks()).containsExactlyInAnyOrder(first, second);
  }

  @Test
  @DisplayName("OpenAI options keep their settings and carry the tools")
  void openAiCarriesTheTools() {
    OpenAiChatOptions options =
        OpenAiChatOptions.builder().model("gpt-4o").temperature(0.5).maxTokens(100).build();

    EngineChatOptions result =
        toolOptions.withTools(wrap(options), List.of(engineTool("a", first)));

    assertThat(result.nativeOptions()).isInstanceOf(OpenAiChatOptions.class);
    OpenAiChatOptions openAi = (OpenAiChatOptions) result.nativeOptions();
    assertThat(openAi.getModel()).isEqualTo("gpt-4o");
    assertThat(openAi.getTemperature()).isEqualTo(0.5);
    assertThat(openAi.getMaxTokens()).isEqualTo(100);
    assertThat(openAi.getToolCallbacks()).containsExactly(first);
  }

  @Test
  @DisplayName("options of another provider are returned as they came")
  void otherOptionsAreKept() {
    EngineChatOptions options = wrap(mock(ChatOptions.class));

    assertThat(toolOptions.withTools(options, List.of(engineTool("a", first)))).isSameAs(options);
  }

  @Test
  @DisplayName("no options stay no options")
  void nullStaysNull() {
    assertThat(toolOptions.withTools(null, List.of(engineTool("a", first)))).isNull();
  }

  @Test
  @DisplayName("a tool that did not come through Spring AI is refused")
  void foreignToolIsRefused() {
    EngineTool foreign = new EngineTool("a", "", "{}", json -> "", null);
    EngineChatOptions options = wrap(OpenAiChatOptions.builder().model("gpt-4o").build());

    assertThatThrownBy(() -> toolOptions.withTools(options, List.of(foreign)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a");
  }
}
