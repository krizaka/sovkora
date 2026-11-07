package com.krizaka.sovkora.persistence.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.persistence.domain.model.SpeechVoice;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.CatalogModelEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.CatalogModelRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpeechVoiceManagerImplTest {

  @Mock private CatalogModelRepository repository;
  @InjectMocks private SpeechVoiceManagerImpl voices;

  private static CatalogModelEntity voice(String name, String locale) {
    CatalogModelEntity entity = new CatalogModelEntity();
    entity.setModelName(name);
    entity.setCategory("speech");
    entity.setLocale(locale);
    entity.setLocaleDefault(true);
    return entity;
  }

  @Test
  @DisplayName("A locale with a declared default gets that voice")
  void exactLocale() {
    when(repository.findByCategoryAndLocaleAndLocaleDefaultTrue("speech", "fr-FR"))
        .thenReturn(Optional.of(voice("piper-fr-siwis-medium", "fr-FR")));

    assertThat(voices.defaultVoice("fr-FR"))
        .contains(new SpeechVoice("piper-fr-siwis-medium", "fr-FR"));
  }

  @Test
  @DisplayName("fr-CA, with no voice of its own, is served fr-FR — and the voice says fr-FR")
  void sameLanguageFallbackSaysWhichLocale() {
    when(repository.findByCategoryAndLocaleAndLocaleDefaultTrue("speech", "fr-CA"))
        .thenReturn(Optional.empty());
    when(repository.findByCategoryAndLocaleDefaultTrueOrderByLocaleAsc("speech"))
        .thenReturn(
            List.of(voice("piper-en-medium-ryan", "en"), voice("piper-fr-siwis-medium", "fr-FR")));

    assertThat(voices.defaultVoice("fr-CA"))
        .contains(new SpeechVoice("piper-fr-siwis-medium", "fr-FR"));
  }

  @Test
  @DisplayName("A language with no voice gets none, never another language's")
  void noVoiceIsEmpty() {
    when(repository.findByCategoryAndLocaleAndLocaleDefaultTrue("speech", "nl-BE"))
        .thenReturn(Optional.empty());
    when(repository.findByCategoryAndLocaleDefaultTrueOrderByLocaleAsc("speech"))
        .thenReturn(List.of(voice("piper-en-medium-ryan", "en")));

    assertThat(voices.defaultVoice("nl-BE")).isEmpty();
    assertThat(voices.defaultVoice(" ")).isEmpty();
  }

  @Test
  @DisplayName(
      "A named voice is returned with its declared locale; one without a locale is not a voice")
  void namedVoice() {
    when(repository.findByCategoryAndModelName("speech", "piper-fr-tom-medium"))
        .thenReturn(Optional.of(voice("piper-fr-tom-medium", "fr-FR")));
    when(repository.findByCategoryAndModelName("speech", "tts-1"))
        .thenReturn(Optional.of(voice("tts-1", null)));

    assertThat(voices.findVoice("piper-fr-tom-medium"))
        .contains(new SpeechVoice("piper-fr-tom-medium", "fr-FR"));
    assertThat(voices.findVoice("tts-1")).isEmpty();
  }
}
