package com.krizaka.sovkora.core.domain.model.audio;

import com.krizaka.sovkora.core.domain.model.AiRequest;
import com.krizaka.sovkora.core.domain.model.Context;
import java.util.Map;

/**
 * Standardized audio request record for Text-To-Speech generation.
 *
 * @param prompt The text content to convert to speech.
 * @param voice The voice model name (defaults to "alloy").
 * @param model The target model name (defaults to "tts-1").
 * @param settings Additional provider-specific settings.
 * @param context The execution context carrying user preferences.
 */
public record AudioRequest(
    String prompt, String voice, String model, Map<String, Object> settings, Context context)
    implements AiRequest {

  /**
   * The settings key a caller declares the audio format it wants under ({@code wav}, {@code mp3},
   * {@code opus}…). Absent, the engine's own default applies. The voice turn asks for {@code wav}
   * because its duration can then be read from the file rather than guessed (ADR-076).
   */
  public static final String RESPONSE_FORMAT = "responseFormat";

  public AudioRequest {
    AiRequest.requireValid(prompt, context);
    voice = voice != null && !voice.isBlank() ? voice : "alloy";
    model = model != null && !model.isBlank() ? model : "tts-1";
    settings = settings != null ? Map.copyOf(settings) : Map.of();
  }

  /**
   * The audio format the caller declared, if any.
   *
   * @return the format, or {@code null} for the engine's default
   */
  public String responseFormat() {
    Object format = settings.get(RESPONSE_FORMAT);
    return format instanceof String declared && !declared.isBlank() ? declared : null;
  }
}
