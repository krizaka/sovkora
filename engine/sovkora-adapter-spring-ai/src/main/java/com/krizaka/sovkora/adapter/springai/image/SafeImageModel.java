package com.krizaka.sovkora.adapter.springai.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

/**
 * Decorator for {@link ImageModel} that turns an engine failure into a clear one.
 *
 * <p>It used to catch every runtime exception and return a 1x1 transparent PNG so the playground
 * "stayed stable". That sealed a placeholder as the run's output and reported SUCCEEDED: a timeout
 * (an engine busy with another request), a refused connection (a relay with no GPU behind it) and a
 * 500 all looked like a finished run with an empty picture. §12 Zero-Fallback: a run either has its
 * image or fails, by name — nothing here fabricates one.
 */
public class SafeImageModel implements ImageModel {

  private static final Logger logger = LoggerFactory.getLogger(SafeImageModel.class);

  private final ImageModel delegate;

  public SafeImageModel(ImageModel delegate) {
    this.delegate = delegate;
  }

  @Override
  public ImageResponse call(ImagePrompt prompt) {
    try {
      return delegate.call(prompt);
    } catch (RuntimeException e) {
      logger.error("Image generation call failed: {}", e.toString());
      throw new IllegalStateException(
          "the image engine did not return an image: " + e.getMessage(), e);
    }
  }
}
