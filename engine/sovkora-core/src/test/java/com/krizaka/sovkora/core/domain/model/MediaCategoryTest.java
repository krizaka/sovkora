package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MediaCategoryTest {

  @Test
  void values_containsAllCategories() {
    assertEquals(7, MediaCategory.values().length);
  }

  @Test
  void value_returnsLowercase() {
    assertEquals("chat", MediaCategory.CHAT.value());
    assertEquals("image", MediaCategory.IMAGE.value());
    assertEquals("audio", MediaCategory.AUDIO.value());
    assertEquals("video", MediaCategory.VIDEO.value());
    assertEquals("speech", MediaCategory.SPEECH.value());
    assertEquals("theme", MediaCategory.THEME.value());
    assertEquals("embedding", MediaCategory.EMBEDDING.value());
  }

  @Test
  void toString_returnsValue() {
    assertEquals("chat", MediaCategory.CHAT.toString());
    assertEquals("image", MediaCategory.IMAGE.toString());
  }

  @Test
  void valueOf_roundTrips() {
    assertEquals(MediaCategory.CHAT, MediaCategory.valueOf("CHAT"));
    assertEquals(MediaCategory.VIDEO, MediaCategory.valueOf("VIDEO"));
  }
}
