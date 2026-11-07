package com.krizaka.sovkora.adapter.springai.chunking;

import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;

/** Interface representing a strategy to chunk raw data into Spring AI Document representations. */
public interface Chunker {

  /**
   * Chunks a raw content string into a list of {@link Document} elements.
   *
   * @param content The raw content string.
   * @param metadata Contextual metadata to attach to each chunk.
   * @return A list of chunked {@link Document} items.
   */
  List<Document> chunk(String content, Map<String, Object> metadata);
}
