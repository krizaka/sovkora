package com.krizaka.sovkora.adapter.springai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolAnswerCodec;
import com.krizaka.sovkora.persistence.domain.model.ChatMessageDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.ChatMemoryPersistenceProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;

class PersistedChatMemoryStoreTest {

  private final ChatMemoryPersistenceProvider provider = mock(ChatMemoryPersistenceProvider.class);
  private final PersistedChatMemoryStore store =
      new PersistedChatMemoryStore(provider, new MarkerCodec());
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final Logger logger = (Logger) LoggerFactory.getLogger(PersistedChatMemoryStore.class);

  @BeforeEach
  void attach() {
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void detach() {
    logger.detachAppender(logs);
  }

  /** Writes the messages, then reads back what the store persisted. */
  @SuppressWarnings("unchecked")
  private List<EngineMessage> roundTrip(List<EngineMessage> messages) {
    store.append("c-1", messages);
    ArgumentCaptor<List<ChatMessageDto>> written = ArgumentCaptor.forClass(List.class);
    verify(provider).appendAll(written.capture());
    when(provider.findRecent(anyString(), anyInt())).thenReturn(written.getValue());
    return store.findRecent("c-1", 10);
  }

  @Test
  @DisplayName("[#27] a tool answer is stored through the codec and read back as a tool")
  void toolResponseRoundTrips() {
    List<EngineMessage> back =
        roundTrip(List.of(new EngineMessage(EngineMessage.Role.TOOL, "", "native")));

    assertThat(back)
        .singleElement()
        .extracting(EngineMessage::role)
        .isEqualTo(EngineMessage.Role.TOOL);
    assertThat(back.getFirst().nativeMessage()).isEqualTo("native");
  }

  @Test
  @DisplayName("[#27] user, assistant and system messages keep their type")
  void otherTypesRoundTrip() {
    List<EngineMessage> back =
        roundTrip(
            List.of(
                EngineMessage.system("s"), EngineMessage.user("u"), EngineMessage.assistant("a")));

    assertThat(back)
        .extracting(EngineMessage::role)
        .containsExactly(
            EngineMessage.Role.SYSTEM, EngineMessage.Role.USER, EngineMessage.Role.ASSISTANT);
    assertThat(back).extracting(EngineMessage::text).containsExactly("s", "u", "a");
  }

  @Test
  @DisplayName(
      "[#27] a persistence failure degrades and logs the stack trace on read, write, clear")
  void failuresKeepTheirStackTrace() {
    QueryTimeoutException failure = new QueryTimeoutException("pool timeout");
    when(provider.findRecent(anyString(), anyInt())).thenThrow(failure);
    doThrow(failure).when(provider).appendAll(anyList());
    doThrow(failure).when(provider).deleteByConversationId(anyString());

    assertThat(store.findRecent("c-1", 5)).isEmpty();
    store.append("c-1", new ArrayList<>(List.of(EngineMessage.user("hi"))));
    store.clear("c-1");

    assertThat(logs.list).hasSize(3);
    assertThat(logs.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNotNull());
  }

  /** A stand-in codec: a message with a native payload is stored as {@code tool:<payload>}. */
  private static final class MarkerCodec implements ToolAnswerCodec {
    @Override
    public Optional<String> encode(EngineMessage message) {
      return message.nativeMessage() == null
          ? Optional.empty()
          : Optional.of("tool:" + message.nativeMessage());
    }

    @Override
    public EngineMessage decode(String stored) {
      return new EngineMessage(
          EngineMessage.Role.TOOL, "", stored.substring(stored.indexOf(':') + 1));
    }
  }
}
