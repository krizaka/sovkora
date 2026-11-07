package com.krizaka.sovkora.core.domain.model.audio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AudioResponseTest {

  @Test
  void validConstruction() {
    var response = new AudioResponse(new byte[] {1, 2, 3}, "wav");
    assertArrayEquals(new byte[] {1, 2, 3}, response.audioData());
    assertEquals("wav", response.format());
  }

  @Test
  void nullFormat_defaultsToMp3() {
    var response = new AudioResponse(new byte[] {1}, null);
    assertEquals("mp3", response.format());
  }

  @Test
  void blankFormat_defaultsToMp3() {
    var response = new AudioResponse(new byte[] {1}, "  ");
    assertEquals("mp3", response.format());
  }

  @Test
  void nullAudioData_throws() {
    assertThrows(NullPointerException.class, () -> new AudioResponse(null, "mp3"));
  }

  @Test
  void emptyAudioData_throws() {
    assertThrows(IllegalArgumentException.class, () -> new AudioResponse(new byte[0], "mp3"));
  }

  @Test
  void equality_withSameData() {
    var r1 = new AudioResponse(new byte[] {1, 2}, "mp3");
    var r2 = new AudioResponse(new byte[] {1, 2}, "mp3");
    assertEquals(r1, r2);
    assertEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void toString_showsSize() {
    var response = new AudioResponse(new byte[] {1, 2, 3}, "mp3");
    assertTrue(response.toString().contains("3 bytes"));
  }
}
