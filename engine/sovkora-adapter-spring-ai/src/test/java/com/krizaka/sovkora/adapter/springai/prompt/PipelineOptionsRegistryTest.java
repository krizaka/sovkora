package com.krizaka.sovkora.adapter.springai.prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.ChatOptions;

@ExtendWith(MockitoExtension.class)
class PipelineOptionsRegistryTest {

  @Mock private PipelineOptionsStrategy matchingStrategy;

  @Mock private PipelineOptionsStrategy nonMatchingStrategy;

  @Test
  void build_withMatchingStrategy_returnsOptions() {
    ChatOptions expectedOptions = mock(ChatOptions.class);
    when(matchingStrategy.supports("openai")).thenReturn(true);
    when(matchingStrategy.buildOptions("gpt-4", 0.7)).thenReturn(expectedOptions);

    var registry = new PipelineOptionsRegistry(List.of(matchingStrategy));
    ChatOptions result = registry.build("openai", "gpt-4", 0.7);

    assertSame(expectedOptions, result);
  }

  @Test
  void build_noMatchingStrategy_throwsException() {
    when(nonMatchingStrategy.supports("unknown")).thenReturn(false);

    var registry = new PipelineOptionsRegistry(List.of(nonMatchingStrategy));

    assertThrows(IllegalArgumentException.class, () -> registry.build("unknown", "model", 0.5));
  }

  @Test
  void build_multipleStrategies_usesFirstMatch() {
    ChatOptions expectedOptions = mock(ChatOptions.class);
    when(nonMatchingStrategy.supports("openai")).thenReturn(false);
    when(matchingStrategy.supports("openai")).thenReturn(true);
    when(matchingStrategy.buildOptions("gpt-4", 0.7)).thenReturn(expectedOptions);

    var registry = new PipelineOptionsRegistry(List.of(nonMatchingStrategy, matchingStrategy));
    ChatOptions result = registry.build("openai", "gpt-4", 0.7);

    assertSame(expectedOptions, result);
  }

  @Test
  void constructor_nullList_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new PipelineOptionsRegistry(null));
  }
}
