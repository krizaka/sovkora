package com.krizaka.sovkora.core.domain.model.image;

import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.sovkora.core.domain.model.Context;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ImageRequestTest {

  private final Context ctx = new Context("user", "conv", Map.of(), Set.of());

  @Test
  void validConstruction() {
    var request = new ImageRequest("A sunset", 512, 512, "dall-e-3", Map.of("quality", "hd"), ctx);
    assertEquals("A sunset", request.prompt());
    assertEquals(512, request.width());
    assertEquals(512, request.height());
    assertEquals("dall-e-3", request.model());
    assertEquals("hd", request.settings().get("quality"));
  }

  @Test
  void nullSettings_defaultsToEmptyMap() {
    var request = new ImageRequest("prompt", null, null, null, null, ctx);
    assertTrue(request.settings().isEmpty());
  }

  @Test
  void nullPrompt_throws() {
    assertThrows(
        NullPointerException.class, () -> new ImageRequest(null, null, null, null, null, ctx));
  }

  @Test
  void sixArgumentRequest_leavesTheDirectionToTheRecipe() {
    var request = new ImageRequest("prompt", null, null, null, null, ctx);
    assertEquals(ImageDirection.none(), request.direction());
  }

  @Test
  void directionIsCarried() {
    var direction = new ImageDirection("text", 7L, 8);
    var request = new ImageRequest("prompt", 1024, 1280, null, null, ctx, direction);
    assertEquals(direction, request.direction());
  }
}
