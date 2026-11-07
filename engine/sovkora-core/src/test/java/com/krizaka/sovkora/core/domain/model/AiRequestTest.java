package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiRequestTest {

  @Test
  void requireValid_validInputs_noException() {
    var context = new Context("user", "conv", Map.of(), Set.of());
    assertDoesNotThrow(() -> AiRequest.requireValid("Hello world", context));
  }

  @Test
  void requireValid_nullPrompt_throws() {
    var context = new Context("user", "conv", Map.of(), Set.of());
    assertThrows(NullPointerException.class, () -> AiRequest.requireValid(null, context));
  }

  @Test
  void requireValid_blankPrompt_throws() {
    var context = new Context("user", "conv", Map.of(), Set.of());
    assertThrows(IllegalArgumentException.class, () -> AiRequest.requireValid("  ", context));
  }

  @Test
  void requireValid_emptyPrompt_throws() {
    var context = new Context("user", "conv", Map.of(), Set.of());
    assertThrows(IllegalArgumentException.class, () -> AiRequest.requireValid("", context));
  }

  @Test
  void requireValid_nullContext_throws() {
    assertThrows(NullPointerException.class, () -> AiRequest.requireValid("prompt", null));
  }
}
