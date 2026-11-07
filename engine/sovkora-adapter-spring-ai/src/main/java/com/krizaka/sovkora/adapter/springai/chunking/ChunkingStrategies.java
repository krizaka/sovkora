package com.krizaka.sovkora.adapter.springai.chunking;

import java.util.*;
import org.springframework.ai.document.Document;

/** Pre-built, reusable chunking strategies library. */
public enum ChunkingStrategies implements Chunker {
  PLAIN_TEXT {
    @Override
    public List<Document> chunk(String content, Map<String, Object> metadata) {
      if (content == null || content.isBlank()) {
        return Collections.emptyList();
      }
      return Arrays.stream(content.split("\\n\\n+"))
          .map(String::trim)
          .filter(trimmed -> !trimmed.isEmpty())
          .map(trimmed -> new Document(trimmed, new HashMap<>(metadata)))
          .toList();
    }
  },

  MARKDOWN_CHUNKERS {
    @Override
    public List<Document> chunk(String content, Map<String, Object> metadata) {
      if (content == null || content.isBlank()) {
        return Collections.emptyList();
      }
      return Arrays.stream(content.split("(?m)^(?=#{1,6}\\s+)"))
          .map(String::trim)
          .filter(trimmed -> !trimmed.isEmpty())
          .map(trimmed -> new Document(trimmed, new HashMap<>(metadata)))
          .toList();
    }
  },

  JSON_ARRAY {
    @Override
    public List<Document> chunk(String content, Map<String, Object> metadata) {
      if (content == null || content.isBlank()) {
        return Collections.emptyList();
      }
      return JsonChunkMapper.parseJsonArray(content)
          .map(
              list ->
                  list.stream()
                      .filter(Objects::nonNull)
                      .map(item -> JsonChunkMapper.toDocument(item, metadata))
                      .toList())
          .orElseGet(() -> PLAIN_TEXT.chunk(content, metadata));
    }
  };

  /**
   * Resolves a strategy by its configuration token name.
   *
   * @param strategyName The configuration token name.
   * @return The matched chunker strategy, defaulting to PLAIN_TEXT if unmatched.
   */
  public static Chunker resolve(String strategyName) {
    if (strategyName == null) {
      return PLAIN_TEXT;
    }
    try {
      return valueOf(strategyName.toUpperCase().trim());
    } catch (IllegalArgumentException e) {
      return PLAIN_TEXT;
    }
  }
}
