package com.krizaka.sovkora.adapter.springai.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.ToolResponseMessage;

class SpringAiToolAnswerCodecTest {

  private final SpringAiToolAnswerCodec codec = new SpringAiToolAnswerCodec();

  @Test
  void aToolAnswerRoundTripsWithItsIdAndName() {
    ToolResponseMessage tool =
        ToolResponseMessage.builder()
            .responses(
                List.of(new ToolResponseMessage.ToolResponse("call-1", "weather", "{\"c\":21}")))
            .build();

    String stored =
        codec.encode(new EngineMessage(EngineMessage.Role.TOOL, "", tool)).orElseThrow();
    EngineMessage back = codec.decode(stored);

    assertThat(back.role()).isEqualTo(EngineMessage.Role.TOOL);
    ToolResponseMessage read = (ToolResponseMessage) back.nativeMessage();
    assertThat(read.getResponses()).hasSize(1);
    assertThat(read.getResponses().getFirst().id()).isEqualTo("call-1");
    assertThat(read.getResponses().getFirst().name()).isEqualTo("weather");
    assertThat(read.getResponses().getFirst().responseData()).isEqualTo("{\"c\":21}");
  }

  @Test
  void aPlainMessageHasNoToolAnswer() {
    assertThat(codec.encode(EngineMessage.user("hi"))).isEmpty();
  }

  @Test
  void aRowWrittenBeforeAnswersWereStructuredIsReadAsPlainText() {
    EngineMessage read = codec.decode("sunny");

    assertThat(read.nativeMessage()).isInstanceOf(ToolResponseMessage.class);
    assertThat(
            ((ToolResponseMessage) read.nativeMessage()).getResponses().getFirst().responseData())
        .isEqualTo("sunny");
  }
}
