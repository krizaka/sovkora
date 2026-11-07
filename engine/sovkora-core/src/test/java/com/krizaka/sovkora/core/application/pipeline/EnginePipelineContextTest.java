package com.krizaka.sovkora.core.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EnginePipelineContextTest {

  @Test
  void fourArgConstructor_defaultsCloseablesToEmpty() {
    var ctx = new EnginePipelineContext("ollama", "conv-1", "hello", null);
    assertThat(ctx.closeables()).isEmpty();
  }

  @Test
  void fiveArgConstructor_copiesCloseables() {
    AutoCloseable closeable = () -> {};
    var ctx = new EnginePipelineContext("openai", "conv-2", "test", null, List.of(closeable));
    assertThat(ctx.closeables()).hasSize(1);
  }

  @Test
  void nullCloseables_defaultsToEmptyList() {
    var ctx = new EnginePipelineContext("openai", "conv-3", "test", null, null);
    assertThat(ctx.closeables()).isEmpty();
  }

  @Test
  void recordAccessors_returnExpectedValues() {
    var ctx = new EnginePipelineContext("gemini", "conv-4", "prompt text", null);
    assertThat(ctx.provider()).isEqualTo("gemini");
    assertThat(ctx.conversationId()).isEqualTo("conv-4");
    assertThat(ctx.promptText()).isEqualTo("prompt text");
    assertThat(ctx.toPrompt()).isNull();
  }
}
