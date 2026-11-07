package com.krizaka.sovkora.engine.persistence.api.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SpeechVoiceTest {

  @Test
  void carries_theModelAndTheLocaleItDeclares() {
    SpeechVoice voice = new SpeechVoice("piper-fr-siwis-medium", "fr-FR");

    assertEquals("piper-fr-siwis-medium", voice.modelName());
    assertEquals("fr-FR", voice.locale());
  }

  @Test
  void refuses_aVoiceWithoutALocale() {
    assertThrows(IllegalArgumentException.class, () -> new SpeechVoice("piper-x", " "));
    assertThrows(IllegalArgumentException.class, () -> new SpeechVoice(" ", "fr-FR"));
  }

  @Test
  void reads_theLanguageOfATag() {
    assertEquals("fr", SpeechVoice.languageOf("fr-CA"));
    assertEquals("en", SpeechVoice.languageOf("EN"));
    assertEquals("", SpeechVoice.languageOf(null));
  }
}
