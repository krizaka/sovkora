package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.audio.AudioRequest;
import com.krizaka.sovkora.core.domain.model.audio.AudioResponse;

/**
 * Port interface for executing text-to-speech audio generation inference against AI speech models.
 */
public interface AudioGeneratorClient {

  /**
   * Generates speech audio based on the text and parameters in the request.
   *
   * @param request The audio request parameters.
   * @return The generated audio response containing raw bytes and format.
   */
  AudioResponse generateAudio(AudioRequest request);
}
