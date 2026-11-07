package com.krizaka.sovkora.core.domain.model.image;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ImageResponseTest {

  @Test
  void validConstruction() {
    var response = new ImageResponse(new byte[] {1, 2, 3}, "http://example.com/img.png", "png");
    assertArrayEquals(new byte[] {1, 2, 3}, response.imageData());
    assertEquals("http://example.com/img.png", response.url());
    assertEquals("png", response.format());
  }

  @Test
  void nullFields_allowed() {
    var response = new ImageResponse(null, null, null);
    assertNull(response.imageData());
    assertNull(response.url());
    assertNull(response.format());
  }

  @Test
  void equality_withSameData() {
    var r1 = new ImageResponse(new byte[] {1, 2}, "url", "png");
    var r2 = new ImageResponse(new byte[] {1, 2}, "url", "png");
    assertEquals(r1, r2);
    assertEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void toString_showsSize() {
    var response = new ImageResponse(new byte[] {1, 2, 3}, "url", "png");
    assertTrue(response.toString().contains("3 bytes"));
  }
}
