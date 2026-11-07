package com.krizaka.sovkora.core.domain.model.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.domain.model.Context;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [#158] A reference picture is validated at the boundary, and travels with its request. */
class ImageReferenceTest {

  private static final byte[] PICTURE = {1, 2, 3};

  @Test
  @DisplayName("a PNG, JPEG or WebP between weight 0 and 1 is a reference; its bytes are copied")
  void aValidReference() {
    byte[] bytes = PICTURE.clone();
    ImageReference reference = new ImageReference(bytes, " IMAGE/PNG ", 0.5);
    bytes[0] = 9;

    assertThat(reference.mediaType()).isEqualTo("image/png");
    assertThat(reference.image()).containsExactly(1, 2, 3);
    assertThat(reference.partial()).isTrue();
    assertThat(new ImageReference(PICTURE, "image/webp", 1).partial()).isFalse();
    assertThat(reference).isEqualTo(new ImageReference(PICTURE, "image/png", 0.5));
  }

  @Test
  @DisplayName("empty, oversized, not a still picture, or a weight outside [0, 1] is refused")
  void invalidReferencesAreRefused() {
    assertThatThrownBy(() -> new ImageReference(new byte[0], "image/png", 1))
        .hasMessageContaining("empty");
    assertThatThrownBy(
            () -> new ImageReference(new byte[ImageReference.MAX_BYTES + 1], "image/png", 1))
        .hasMessageContaining("at most");
    assertThatThrownBy(() -> new ImageReference(PICTURE, "video/mp4", 1))
        .hasMessageContaining("PNG, JPEG or WebP");
    assertThatThrownBy(() -> new ImageReference(PICTURE, "image/png", 1.5))
        .hasMessageContaining("between 0 and 1");
    assertThatThrownBy(() -> new ImageReference(PICTURE, "image/png", Double.NaN))
        .hasMessageContaining("between 0 and 1");
  }

  @Test
  @DisplayName("a request keeps its references in order, defaults to none, and caps their count")
  void aRequestCarriesItsReferences() {
    ImageReference owl = new ImageReference(PICTURE, "image/png", 1);
    ImageReference duck = new ImageReference(new byte[] {4}, "image/jpeg", 1);
    ImageRequest request =
        new ImageRequest(
            "an owl", 1360, 768, "m", Map.of(), Context.anonymous(), null, List.of(owl, duck));

    assertThat(request.references()).containsExactly(owl, duck);
    assertThat(
            new ImageRequest("an owl", null, null, null, Map.of(), Context.anonymous())
                .references())
        .isEmpty();
    assertThatThrownBy(
            () ->
                new ImageRequest(
                    "an owl",
                    null,
                    null,
                    null,
                    Map.of(),
                    Context.anonymous(),
                    null,
                    Collections.nCopies(ImageRequest.MAX_REFERENCES + 1, owl)))
        .hasMessageContaining("at most " + ImageRequest.MAX_REFERENCES);
  }

  @Test
  @DisplayName("an image response carries what the engine could not honour, blank meaning nothing")
  void aResponseCarriesItsNotice() {
    assertThat(new ImageResponse(PICTURE, null, "png", null, "  weight not applied ").notice())
        .isEqualTo("weight not applied");
    assertThat(new ImageResponse(PICTURE, null, "png", null, " ").notice()).isNull();
    assertThat(new ImageResponse(PICTURE, null, "png").notice()).isNull();
  }
}
