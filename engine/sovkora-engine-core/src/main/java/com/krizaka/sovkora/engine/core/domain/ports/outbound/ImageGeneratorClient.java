package com.krizaka.sovkora.engine.core.domain.ports.outbound;

import com.krizaka.sovkora.engine.core.domain.model.image.ImageRequest;
import com.krizaka.sovkora.engine.core.domain.model.image.ImageResponse;

/** Port interface for executing image generation inference against AI image models. */
public interface ImageGeneratorClient {

  /**
   * Generates an image based on the request parameters.
   *
   * @param request The image request payload.
   * @return The generated image response.
   */
  ImageResponse generateImage(ImageRequest request);
}
