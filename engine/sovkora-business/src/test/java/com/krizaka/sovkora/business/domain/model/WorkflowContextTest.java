package com.krizaka.sovkora.business.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WorkflowContext — self-validating domain record")
class WorkflowContextTest {

  @Nested
  @DisplayName("Compact Constructor Validation")
  class CompactConstructorValidation {

    @Test
    @DisplayName("Rejects null contextId with NullPointerException")
    void rejectsNullContextId() {
      assertThatThrownBy(() -> new WorkflowContext(null, "instructions", "PRO", null, null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("contextId");
    }

    @Test
    @DisplayName("Rejects null systemInstructions with NullPointerException")
    void rejectsNullSystemInstructions() {
      assertThatThrownBy(() -> new WorkflowContext("ctx-1", null, "PRO", null, null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("systemInstructions");
    }

    @Test
    @DisplayName("Defaults userTier to DEFAULT when null")
    void defaultsUserTierWhenNull() {
      var ctx = new WorkflowContext("ctx-1", "instructions", null, null, null, null);
      assertThat(ctx.userTier()).isEqualTo("DEFAULT");
    }

    @Test
    @DisplayName("Defaults userTier to DEFAULT when blank")
    void defaultsUserTierWhenBlank() {
      var ctx = new WorkflowContext("ctx-1", "instructions", "   ", null, null, null);
      assertThat(ctx.userTier()).isEqualTo("DEFAULT");
    }

    @Test
    @DisplayName("Strips whitespace from userTier")
    void stripsUserTierWhitespace() {
      var ctx = new WorkflowContext("ctx-1", "instructions", "  PRO  ", null, null, null);
      assertThat(ctx.userTier()).isEqualTo("PRO");
    }
  }

  @Nested
  @DisplayName("Defensive Copying")
  class DefensiveCopying {

    @Test
    @DisplayName("forcedInterceptors is defensively copied and unmodifiable")
    void forcedInterceptorsDefensivelyCopied() {
      Set<String> mutable = new HashSet<>();
      mutable.add("RefinerInterceptor");
      var ctx = new WorkflowContext("ctx-1", "instructions", "PRO", mutable, null, null);

      mutable.add("RouterInterceptor");
      assertThat(ctx.forcedInterceptors()).containsExactly("RefinerInterceptor");
      assertThatThrownBy(() -> ctx.forcedInterceptors().add("illegal"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("skippedInterceptors is defensively copied and unmodifiable")
    void skippedInterceptorsDefensivelyCopied() {
      Set<String> mutable = new HashSet<>();
      mutable.add("MemoryInterceptor");
      var ctx = new WorkflowContext("ctx-1", "instructions", "PRO", null, mutable, null);

      mutable.add("RagInterceptor");
      assertThat(ctx.skippedInterceptors()).containsExactly("MemoryInterceptor");
      assertThatThrownBy(() -> ctx.skippedInterceptors().add("illegal"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("metadata is defensively copied and unmodifiable")
    void metadataDefensivelyCopied() {
      Map<String, Object> mutable = new HashMap<>();
      mutable.put("key", "value");
      var ctx = new WorkflowContext("ctx-1", "instructions", "PRO", null, null, mutable);

      mutable.put("rogue", "data");
      assertThat(ctx.metadata()).containsOnlyKeys("key");
      assertThatThrownBy(() -> ctx.metadata().put("illegal", "value"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Null collections default to empty unmodifiable sets/maps")
    void nullCollectionsDefaultToEmpty() {
      var ctx = new WorkflowContext("ctx-1", "instructions", "PRO", null, null, null);
      assertThat(ctx.forcedInterceptors()).isEmpty();
      assertThat(ctx.skippedInterceptors()).isEmpty();
      assertThat(ctx.metadata()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    @DisplayName("minimal() creates context with DEFAULT tier and empty collections")
    void minimalFactory() {
      var ctx = WorkflowContext.minimal("ctx-1", "instructions");
      assertThat(ctx.contextId()).isEqualTo("ctx-1");
      assertThat(ctx.systemInstructions()).isEqualTo("instructions");
      assertThat(ctx.userTier()).isEqualTo("DEFAULT");
      assertThat(ctx.forcedInterceptors()).isEmpty();
      assertThat(ctx.skippedInterceptors()).isEmpty();
      assertThat(ctx.metadata()).isEmpty();
    }
  }

  @Test
  @DisplayName("Full construction with all fields populated")
  void fullConstruction() {
    var ctx =
        new WorkflowContext(
            "session-42",
            "You are a sovereign assistant.",
            "ENTERPRISE",
            Set.of("RefinerInterceptor"),
            Set.of("MemoryInterceptor"),
            Map.of("orgId", "krizaka"));

    assertThat(ctx.contextId()).isEqualTo("session-42");
    assertThat(ctx.systemInstructions()).isEqualTo("You are a sovereign assistant.");
    assertThat(ctx.userTier()).isEqualTo("ENTERPRISE");
    assertThat(ctx.forcedInterceptors()).containsExactly("RefinerInterceptor");
    assertThat(ctx.skippedInterceptors()).containsExactly("MemoryInterceptor");
    assertThat(ctx.metadata()).containsEntry("orgId", "krizaka");
  }
}
