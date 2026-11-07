package com.krizaka.sovkora.engine.persistence.api.domain.model;

import java.util.Locale;

/**
 * A speech voice of the model catalogue and the locale it declares (ADR-076).
 *
 * @param modelName the catalogue's model name, which is what the speech engine is asked for
 * @param locale the BCP 47 tag the voice speaks, as catalogued
 */
public record SpeechVoice(String modelName, String locale) {

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public SpeechVoice {
    if (modelName == null || modelName.isBlank()) {
      throw new IllegalArgumentException("a voice has a model name");
    }
    if (locale == null || locale.isBlank()) {
      throw new IllegalArgumentException("a voice declares the locale it speaks: " + modelName);
    }
  }

  /**
   * The language of a BCP 47 tag — its primary subtag, lower-cased.
   *
   * @param tag a tag such as {@code fr-CA}
   * @return {@code fr}
   */
  public static String languageOf(String tag) {
    String primary = tag == null ? "" : tag.strip();
    int dash = primary.indexOf('-');
    return (dash < 0 ? primary : primary.substring(0, dash)).toLowerCase(Locale.ROOT);
  }
}
