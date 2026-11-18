package com.krizaka.sovkora.conversation.domain.model.reader;

import java.util.Map;

/**
 * The routing of the document readers at the time of one call: the {@code reader.*} rows of {@code
 * document_reader_setting}, as flat keys. The door does not interpret them; it sends them with the
 * pages, and the worker checks each value by its bounds ({@code workers/reader/document_reader/
 * routing.py}). That is what makes switching a reader a row change and nothing else.
 *
 * @param settings the routing keys and their values
 */
public record ReaderRouting(Map<String, String> settings) {

  /** The prefix every routing key carries. */
  public static final String PREFIX = "reader.";

  /** Copies the settings and refuses an empty routing or a key that is not a routing key. */
  public ReaderRouting {
    settings = Map.copyOf(settings);
    if (settings.isEmpty()) {
      throw new IllegalArgumentException("the document reader routing has no setting");
    }
    if (!settings.keySet().stream().allMatch(key -> key.startsWith(PREFIX))) {
      throw new IllegalArgumentException("a document reader setting starts with " + PREFIX);
    }
  }
}
