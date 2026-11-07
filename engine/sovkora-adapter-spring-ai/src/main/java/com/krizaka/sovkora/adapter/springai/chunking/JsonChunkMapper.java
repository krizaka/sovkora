package com.krizaka.sovkora.adapter.springai.chunking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.document.Document;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Utility class for mapping JSON objects to Documents. */
final class JsonChunkMapper {
  /** ObjectMapper instance for JSON serialization and deserialization. */
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  /** Private constructor to prevent instantiation. */
  private JsonChunkMapper() {}

  /**
   * Parses a JSON array string into a list of objects.
   *
   * @param content The JSON array string to parse.
   * @return An Optional containing the list of objects if parsing is successful, empty otherwise.
   */
  static Optional<List<Object>> parseJsonArray(String content) {
    try {
      return Optional.of(JSON_MAPPER.readValue(content, new TypeReference<List<Object>>() {}));
    } catch (JacksonException e) {
      return Optional.empty();
    }
  }

  /**
   * Converts an item to a Document, serializing it to JSON if it's a Map and adding metadata.
   *
   * @param item The item to convert to a Document.
   * @param metadata The metadata to add to the Document.
   * @return The Document containing the serialized item and metadata.
   */
  static Document toDocument(Object item, Map<String, Object> metadata) {
    Map<String, Object> itemMeta = new HashMap<>(metadata);
    String docText = serializeItem(item, itemMeta);
    return new Document(docText, itemMeta);
  }

  /**
   * Serializes an item to a JSON string, adding its properties to the item metadata. If the item is
   * not a Map, it is converted to a string using its toString() method.
   *
   * @param item The item to serialize.
   * @param itemMeta The metadata to add the item's properties to.
   * @return The serialized item as a JSON string, or its toString() representation if it's not a
   *     Map.
   */
  private static String serializeItem(Object item, Map<String, Object> itemMeta) {
    if (!(item instanceof Map<?, ?> mapItem)) {
      return item.toString();
    }
    mapItem.forEach(
        (k, v) -> {
          if (k != null && v != null) {
            itemMeta.put(k.toString(), v);
          }
        });

    return writeJson(mapItem).orElseGet(mapItem::toString);
  }

  /**
   * Writes an object as a JSON string.
   *
   * @param value The object to serialize.
   * @return An Optional containing the JSON string if serialization is successful, empty otherwise.
   */
  private static Optional<String> writeJson(Object value) {
    try {
      return Optional.of(JSON_MAPPER.writeValueAsString(value));
    } catch (JacksonException e) {
      return Optional.empty();
    }
  }
}
