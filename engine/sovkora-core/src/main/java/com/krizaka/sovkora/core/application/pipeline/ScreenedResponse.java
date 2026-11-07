package com.krizaka.sovkora.core.application.pipeline;

import java.util.Map;

/**
 * An answer after an output screen: the text to serve and the notes the screen wants the response
 * to carry (a verdict, a badge) — they join the response metadata.
 *
 * @param text the text to serve
 * @param notes metadata to add to the response (never {@code null})
 */
public record ScreenedResponse(String text, Map<String, Object> notes) {

  /** Defensive copy. */
  public ScreenedResponse {
    notes = notes == null ? Map.of() : Map.copyOf(notes);
  }

  /**
   * @param text the text to serve
   * @return a screened answer with no notes
   */
  public static ScreenedResponse plain(String text) {
    return new ScreenedResponse(text, Map.of());
  }
}
