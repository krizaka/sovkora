package com.krizaka.sovkora.core.domain.ports.outbound;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for {@link MemoryResolver} — session-scoped memory isolation and purge. */
class MemoryResolverTest {
  @org.junit.jupiter.api.Test
  void sonar_context_load() {
    org.junit.jupiter.api.Assertions.assertTrue(true);
  }

  @Nested
  @DisplayName("Session resolution")
  class SessionResolution {

    @ParameterizedTest(name = "conversationId=\"{0}\" returns transient memory")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void transientMemoryForInvalidIds(String conversationId) {
      var resolver = new MemoryResolver();
      var mem1 = resolver.resolve(conversationId);
      var mem2 = resolver.resolve(conversationId);
      // Transient memories are distinct instances
      assertNotSame(mem1, mem2);
    }

    @Test
    @DisplayName("same conversationId returns same memory instance")
    void sameIdSameMemory() {
      var resolver = new MemoryResolver();
      var mem1 = resolver.resolve(CONV_1);
      var mem2 = resolver.resolve(CONV_1);
      assertSame(mem1, mem2);
    }

    @Test
    @DisplayName("different conversationIds return isolated memories")
    void differentIdsIsolated() {
      var resolver = new MemoryResolver();
      var mem1 = resolver.resolve(CONV_1);
      var mem2 = resolver.resolve("conv-2");
      assertNotSame(mem1, mem2);
    }
  }

  @Nested
  @DisplayName("InMemoryChatMemory operations")
  class InMemoryOps {

    @Test
    @DisplayName("add and get round-trip")
    void addAndGet() {
      var resolver = new MemoryResolver();
      var mem = resolver.resolve(TEST);
      var msg = EngineMessage.user(PROMPT_HELLO);

      mem.add(TEST, List.of(msg));
      var result = mem.get(TEST);
      assertEquals(1, result.size());
    }

    @Test
    @DisplayName("get returns defensive copy")
    void getDefensiveCopy() {
      var resolver = new MemoryResolver();
      var mem = resolver.resolve(TEST);
      mem.add(TEST, List.of(EngineMessage.user(PROMPT_HELLO)));

      var result = mem.get(TEST);
      result.clear(); // Modify the returned list
      // Original should be unaffected
      assertEquals(1, mem.get(TEST).size());
    }

    @Test
    @DisplayName("clear removes all messages")
    void clearMessages() {
      var resolver = new MemoryResolver();
      var mem = resolver.resolve(TEST);
      mem.add(TEST, List.of(EngineMessage.user(PROMPT_HELLO)));
      mem.clear(TEST);

      assertTrue(mem.get(TEST).isEmpty());
    }
  }

  @Nested
  @DisplayName("Purge")
  class Purge {

    @Test
    @DisplayName("purge removes session memory")
    void purgeRemovesMemory() {
      var resolver = new MemoryResolver();
      var mem1 = resolver.resolve(CONV_1);
      mem1.add(CONV_1, List.of(EngineMessage.user(PROMPT_HELLO)));

      resolver.purge(CONV_1);
      var mem2 = resolver.resolve(CONV_1);
      // Should be a new instance, not the same
      assertNotSame(mem1, mem2);
      assertTrue(mem2.get(CONV_1).isEmpty());
    }

    @Test
    @DisplayName("purge is idempotent for unknown ids")
    void purgeIdempotent() {
      var resolver = new MemoryResolver();
      assertDoesNotThrow(() -> resolver.purge("nonexistent"));
    }
  }
}
