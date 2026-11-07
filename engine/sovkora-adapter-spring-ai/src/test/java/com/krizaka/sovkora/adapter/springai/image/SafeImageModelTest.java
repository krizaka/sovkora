package com.krizaka.sovkora.adapter.springai.image;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

/** An engine failure fails the call by name; it never becomes a 1x1 "image" sealed as a result. */
class SafeImageModelTest {

  @Test
  void anEngineFailureIsRethrown_notReplacedByAPlaceholder() {
    ImageModel engine = mock(ImageModel.class);
    when(engine.call(any(ImagePrompt.class))).thenThrow(new RuntimeException("Request failed"));

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> new SafeImageModel(engine).call(new ImagePrompt("a lamp")));

    assertTrue(failure.getMessage().contains("did not return an image"));
    assertNotNull(failure.getCause());
  }

  @Test
  void aRealAnswerPassesThroughUntouched() {
    ImageModel engine = mock(ImageModel.class);
    ImageResponse answer = mock(ImageResponse.class);
    when(engine.call(any(ImagePrompt.class))).thenReturn(answer);

    assertSame(answer, new SafeImageModel(engine).call(new ImagePrompt("a lamp")));
  }
}
