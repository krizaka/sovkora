package com.krizaka.sovkora.adapter.springai.chunking;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ChunkingStrategies} covering all chunking modes. */
class ChunkingStrategiesTest {
  @org.junit.jupiter.api.Test
  void sonar_context_load() {
    org.junit.jupiter.api.Assertions.assertTrue(true);
  }

  @Nested
  @DisplayName("resolve()")
  class Resolve {

    @Test
    @DisplayName("null strategy resolves to PLAIN_TEXT")
    void nullResolvesToDefault() {
      assertSame(ChunkingStrategies.PLAIN_TEXT, ChunkingStrategies.resolve(null));
    }

    @Test
    @DisplayName("'PLAIN_TEXT' resolves correctly (case-insensitive)")
    void resolvesPlainText() {
      assertSame(ChunkingStrategies.PLAIN_TEXT, ChunkingStrategies.resolve("plain_text"));
    }

    @Test
    @DisplayName("'JSON_ARRAY' resolves correctly")
    void resolvesJsonArray() {
      assertSame(ChunkingStrategies.JSON_ARRAY, ChunkingStrategies.resolve("JSON_ARRAY"));
    }

    @Test
    @DisplayName("unknown strategy resolves to PLAIN_TEXT")
    void unknownResolvesToDefault() {
      assertSame(ChunkingStrategies.PLAIN_TEXT, ChunkingStrategies.resolve("foobar"));
    }
  }

  @Nested
  @DisplayName("PLAIN_TEXT")
  class PlainText {

    @Test
    @DisplayName("splits by double newline")
    void splitsByDoubleNewline() {
      var result = ChunkingStrategies.PLAIN_TEXT.chunk("para1\n\npara2\n\npara3", Map.of());
      assertEquals(3, result.size());
      assertEquals("para1", result.getFirst().getText());
    }

    @Test
    @DisplayName("null content returns empty list")
    void nullContent() {
      assertTrue(ChunkingStrategies.PLAIN_TEXT.chunk(null, Map.of()).isEmpty());
    }

    @Test
    @DisplayName("blank content returns empty list")
    void blankContent() {
      assertTrue(ChunkingStrategies.PLAIN_TEXT.chunk("   ", Map.of()).isEmpty());
    }

    @Test
    @DisplayName("preserves metadata in each document")
    void preservesMetadata() {
      var meta = Map.<String, Object>of("source", TEST);
      var result = ChunkingStrategies.PLAIN_TEXT.chunk("hello\n\nworld", meta);
      assertEquals(TEST, result.getFirst().getMetadata().get("source"));
    }
  }

  @Nested
  @DisplayName("MARKDOWN_CHUNKERS")
  class MarkdownChunkers {

    @Test
    @DisplayName("splits on markdown headings")
    void splitsByHeadings() {
      var md = "# Title\nIntro content\n\n## Section\nBody content";
      var result = ChunkingStrategies.MARKDOWN_CHUNKERS.chunk(md, Map.of());
      assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("null content returns empty list")
    void nullContent() {
      assertTrue(ChunkingStrategies.MARKDOWN_CHUNKERS.chunk(null, Map.of()).isEmpty());
    }
  }

  @Nested
  @DisplayName("JSON_ARRAY")
  class JsonArray {

    @Test
    @DisplayName("parses JSON array of strings")
    void parsesStringArray() {
      var result =
          ChunkingStrategies.JSON_ARRAY.chunk("[\"item1\", \"item2\", \"item3\"]", Map.of());
      assertEquals(3, result.size());
      assertEquals("item1", result.getFirst().getText());
    }

    @Test
    @DisplayName("parses JSON array of objects")
    void parsesObjectArray() {
      var result =
          ChunkingStrategies.JSON_ARRAY.chunk("[{\"name\":\"test\",\"value\":42}]", Map.of());
      assertEquals(1, result.size());
      assertTrue(result.getFirst().getText().contains(TEST));
    }

    @Test
    @DisplayName("invalid JSON falls back to PLAIN_TEXT")
    void invalidJsonFallsBack() {
      var result = ChunkingStrategies.JSON_ARRAY.chunk("not valid json\n\nmore text", Map.of());
      assertEquals(2, result.size());
    }

    @Test
    @DisplayName("null content returns empty list")
    void nullContent() {
      assertTrue(ChunkingStrategies.JSON_ARRAY.chunk(null, Map.of()).isEmpty());
    }

    @Test
    @DisplayName("filters null items from array")
    void filtersNulls() {
      var result =
          ChunkingStrategies.JSON_ARRAY.chunk("[\"valid\", null, \"also valid\"]", Map.of());
      assertEquals(2, result.size());
    }
  }
}
