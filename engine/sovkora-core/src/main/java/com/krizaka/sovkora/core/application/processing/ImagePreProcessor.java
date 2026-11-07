package com.krizaka.sovkora.core.application.processing;

/**
 * Port interface for pre-processing image input before cognitive analysis.
 *
 * <p>Implementations extract structured metadata from raw image bytes for downstream multi-modal
 * model consumption.
 */
public interface ImagePreProcessor {

  /**
   * Processes raw image bytes into a structured payload containing base64 encoding and dimensions.
   *
   * @param imageBytes The raw image file content.
   * @return A structured payload ready for multi-modal model consumption.
   */
  ProcessedImagePayload process(byte[] imageBytes);
}
