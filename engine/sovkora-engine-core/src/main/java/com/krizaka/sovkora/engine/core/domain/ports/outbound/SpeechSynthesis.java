package com.krizaka.sovkora.engine.core.domain.ports.outbound;

/**
 * The port that turns text into speech audio (#121): the engine calls it and never holds the
 * provider library's text-to-speech model. Its Spring AI implementation lives in {@code
 * sovkora-engine-springai}.
 */
public interface SpeechSynthesis {

  /**
   * Speaks the text.
   *
   * @param text the text to speak
   * @param model the speech model name
   * @param responseFormat the audio format the caller declared, or {@code null} for the default
   * @return the raw audio bytes
   */
  byte[] synthesize(String text, String model, String responseFormat);
}
