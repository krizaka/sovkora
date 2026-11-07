package com.krizaka.sovkora.core.domain.model.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageDirectionTest {

  @Test
  @DisplayName("none() leaves everything to the model's recipe")
  void noneLeavesEverythingToTheRecipe() {
    ImageDirection none = ImageDirection.none();

    assertThat(none.negativePrompt()).isNull();
    assertThat(none.seed()).isNull();
    assertThat(none.steps()).isNull();
  }

  @Test
  @DisplayName("a blank negative prompt is no negative prompt, and one is stripped")
  void blankNegativePromptIsAbsent() {
    assertThat(new ImageDirection("   ", null, null).negativePrompt()).isNull();
    assertThat(new ImageDirection("  text, logo ", null, null).negativePrompt())
        .isEqualTo("text, logo");
  }

  @Test
  @DisplayName(
      "a negative seed, a step count out of range or an endless negative prompt is refused")
  void unhonourableValuesAreRefused() {
    assertThatThrownBy(() -> new ImageDirection(null, -1L, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ImageDirection(null, null, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ImageDirection(null, null, ImageDirection.MAX_STEPS + 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ImageDirection("x".repeat(ImageDirection.MAX_NEGATIVE_PROMPT + 1), null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a seed of zero and the step bounds are accepted")
  void boundsAreInclusive() {
    ImageDirection direction = new ImageDirection(null, 0L, ImageDirection.MAX_STEPS);

    assertThat(direction.seed()).isZero();
    assertThat(direction.steps()).isEqualTo(ImageDirection.MAX_STEPS);
    assertThat(new ImageDirection(null, null, 1).steps()).isEqualTo(1);
  }
}
