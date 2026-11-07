package com.krizaka.sovkora.adapter.springai.chunking;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class JsonChunkMapperTest {

  @Test
  void parseJsonArray_validJsonArray() {
    Optional<List<Object>> result = JsonChunkMapper.parseJsonArray("[1, 2, 3]");
    assertTrue(result.isPresent());
    assertEquals(3, result.get().size());
  }

  @Test
  void parseJsonArray_validMapArray() {
    Optional<List<Object>> result = JsonChunkMapper.parseJsonArray("[{\"key\":\"value\"}]");
    assertTrue(result.isPresent());
    assertEquals(1, result.get().size());
  }

  @Test
  void parseJsonArray_invalidJson_returnsEmpty() {
    Optional<List<Object>> result = JsonChunkMapper.parseJsonArray("not json");
    assertTrue(result.isEmpty());
  }

  @Test
  void parseJsonArray_jsonObject_returnsEmpty() {
    Optional<List<Object>> result = JsonChunkMapper.parseJsonArray("{\"key\":\"value\"}");
    assertTrue(result.isEmpty());
  }

  @Test
  void toDocument_withMapItem_serializesToJson() {
    Map<String, Object> item = Map.of("name", "test", "value", 42);
    Map<String, Object> metadata = Map.of("source", "unit-test");
    Document doc = JsonChunkMapper.toDocument(item, metadata);
    assertNotNull(doc.getText());
    assertTrue(doc.getMetadata().containsKey("source"));
    assertTrue(doc.getMetadata().containsKey("name"));
    assertTrue(doc.getMetadata().containsKey("value"));
  }

  @Test
  void toDocument_withStringItem_usesToString() {
    Map<String, Object> metadata = Map.of("source", "unit-test");
    Document doc = JsonChunkMapper.toDocument("simple text", metadata);
    assertEquals("simple text", doc.getText());
    assertTrue(doc.getMetadata().containsKey("source"));
  }

  @Test
  void toDocument_withNumericItem_usesToString() {
    Map<String, Object> metadata = Map.of();
    Document doc = JsonChunkMapper.toDocument(42, metadata);
    assertEquals("42", doc.getText());
  }
}
