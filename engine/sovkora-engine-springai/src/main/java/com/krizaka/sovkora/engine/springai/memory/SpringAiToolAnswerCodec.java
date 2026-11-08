package com.krizaka.sovkora.engine.springai.memory;

import com.krizaka.sovkora.engine.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.ToolAnswerCodec;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** {@link ToolAnswerCodec} on Spring AI's {@link ToolResponseMessage}. */
public final class SpringAiToolAnswerCodec implements ToolAnswerCodec {

  private static final Logger log = LoggerFactory.getLogger(SpringAiToolAnswerCodec.class);
  private static final JsonMapper TOOL_JSON = new JsonMapper();

  @Override
  public Optional<String> encode(EngineMessage message) {
    if (message.nativeMessage() instanceof ToolResponseMessage tool) {
      return Optional.of(
          TOOL_JSON.writeValueAsString(
              tool.getResponses().stream()
                  .map(r -> new StoredToolResponse(r.id(), r.name(), r.responseData()))
                  .toList()));
    }
    return Optional.empty();
  }

  @Override
  public EngineMessage decode(String stored) {
    List<StoredToolResponse> responses;
    try {
      responses = TOOL_JSON.readValue(stored, new TypeReference<>() {});
    } catch (RuntimeException unreadable) {
      // Written before tool answers were structured: keep the text as the one answer.
      log.debug("Stored tool response is not structured JSON; treating as plain text", unreadable);
      responses = List.of(new StoredToolResponse("", "", stored));
    }
    ToolResponseMessage tool =
        ToolResponseMessage.builder()
            .responses(
                responses.stream()
                    .map(
                        r ->
                            new ToolResponseMessage.ToolResponse(
                                r.id(), r.name(), r.responseData()))
                    .toList())
            .build();
    return new EngineMessage(EngineMessage.Role.TOOL, tool.getText(), tool);
  }

  /** One tool answer as stored in the message content. */
  private record StoredToolResponse(String id, String name, String responseData) {}
}
