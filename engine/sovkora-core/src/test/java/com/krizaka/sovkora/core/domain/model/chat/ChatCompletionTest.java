package com.krizaka.sovkora.core.domain.model.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** What a completion says about the tools the engine itself ran. */
class ChatCompletionTest {

  @Test
  void aCompletionWithoutExecutedToolsHasNone() {
    assertThat(new ChatCompletion("a", null, List.of()).executed()).isEmpty();
    assertThat(new ChatCompletion("a", null).executed()).isEmpty();
  }

  @Test
  void theExecutedToolsAreKeptApartFromTheOnesReturnedToTheClient() {
    var ran = new ToolCall("1", "lookup", "{}");
    var completion = new ChatCompletion("a", null, List.of(), List.of(ran));

    assertThat(completion.executed()).containsExactly(ran);
    assertThat(completion.toolCalls()).isEmpty();
  }
}
