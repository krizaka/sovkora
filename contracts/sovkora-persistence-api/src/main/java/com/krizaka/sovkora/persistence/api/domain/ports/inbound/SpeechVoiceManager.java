package com.krizaka.sovkora.persistence.api.domain.ports.inbound;

import com.krizaka.sovkora.persistence.api.domain.model.SpeechVoice;
import java.util.Optional;

/**
 * The speech voices of the model catalogue, by the locale each one declares (ADR-076).
 *
 * <p>A voice's language is a column, written when the voice is catalogued — never read off its name
 * ({@code piper-fr-…}) or its free-text options. That is the whole reason this port exists beside
 * {@link CatalogModelManager}: a lookup by locale over the rows it already returns would have to
 * infer the language, and "declared, not inferred" (AGENTS.md §12) forbids it.
 */
public interface SpeechVoiceManager {

  /**
   * A catalogued voice by its model name, with the locale it declares.
   *
   * @param modelName the voice's model name
   * @return the voice, or empty when the catalogue holds no speech model of that name
   */
  Optional<SpeechVoice> findVoice(String modelName);

  /**
   * The voice a locale gets when the caller names none.
   *
   * <p>Exactly the locale's declared default, else the default of another locale of the same
   * language — the second answer says which locale it is in, so a caller asking for {@code fr-CA}
   * and served {@code fr-FR} can say so rather than pretend.
   *
   * @param locale a BCP 47 tag such as {@code fr-CA}, {@code fr-FR} or {@code en}
   * @return the default voice, or empty when no voice of that language is catalogued
   */
  Optional<SpeechVoice> defaultVoice(String locale);
}
