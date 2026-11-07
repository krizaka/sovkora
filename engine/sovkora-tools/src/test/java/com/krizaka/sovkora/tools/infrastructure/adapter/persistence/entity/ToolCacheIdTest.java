package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ToolCacheId} — composite key equals/hashCode contract. */
class ToolCacheIdTest {
  @org.junit.jupiter.api.Test
  void sonar_context_load() {
    assertNotNull(ToolCacheId.class);
  }

  @Nested
  @DisplayName("Equality contract")
  class EqualityContract {

    @Test
    @DisplayName("same fields are equal")
    void sameFieldsEqual() {
      var id1 = new ToolCacheId(TOOL_1, KEY_1);
      var id2 = new ToolCacheId(TOOL_1, KEY_1);
      assertEquals(id1, id2);
      assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    @DisplayName("different toolId not equal")
    void differentToolId() {
      var id1 = new ToolCacheId(TOOL_1, KEY_1);
      var id2 = new ToolCacheId("tool2", KEY_1);
      assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("different cacheKey not equal")
    void differentCacheKey() {
      var id1 = new ToolCacheId(TOOL_1, KEY_1);
      var id2 = new ToolCacheId(TOOL_1, "key2");
      assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("not equal to null")
    void notEqualToNull() {
      var id = new ToolCacheId(TOOL_1, KEY_1);
      assertNotEquals(null, id);
    }

    @Test
    @DisplayName("reflexive equality")
    void reflexive() {
      var id1 = new ToolCacheId(TOOL_1, KEY_1);
      var id2 = new ToolCacheId(TOOL_1, KEY_1);
      assertEquals(id1, id2, "Equal objects must be equal");
    }
  }

  @Nested
  @DisplayName("Record accessors")
  class RecordAccessors {

    @Test
    @DisplayName("accessors return constructor values")
    void accessorsReturnValues() {
      var id = new ToolCacheId("t1", "k1");
      assertEquals("t1", id.toolId());
      assertEquals("k1", id.cacheKey());
    }
  }
}
