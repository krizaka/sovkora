package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.model.SpeechVoice;
import com.krizaka.sovkora.persistence.domain.ports.inbound.SpeechVoiceManager;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.CatalogModelEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.CatalogModelRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private implementation of the {@link SpeechVoiceManager} inbound port (ADR-076).
 *
 * <p>Read per turn and not cached: a voice is re-catalogued by an operator, and a turn that kept
 * speaking with the voice of the previous catalogue would be the stale-declaration defect
 * StepDeclarationService exists to avoid.
 */
@Service
@Transactional(readOnly = true)
class SpeechVoiceManagerImpl implements SpeechVoiceManager {

  private static final String SPEECH = "speech";

  private final CatalogModelRepository repository;

  SpeechVoiceManagerImpl(CatalogModelRepository repository) {
    this.repository = Objects.requireNonNull(repository, "CatalogModelRepository cannot be null");
  }

  @Override
  public Optional<SpeechVoice> findVoice(String modelName) {
    if (modelName == null || modelName.isBlank()) {
      return Optional.empty();
    }
    return repository
        .findByCategoryAndModelName(SPEECH, modelName)
        .filter(entity -> entity.getLocale() != null)
        .map(SpeechVoiceManagerImpl::toVoice);
  }

  @Override
  public Optional<SpeechVoice> defaultVoice(String locale) {
    if (locale == null || locale.isBlank()) {
      return Optional.empty();
    }
    Optional<SpeechVoice> exact =
        repository
            .findByCategoryAndLocaleAndLocaleDefaultTrue(SPEECH, locale)
            .map(SpeechVoiceManagerImpl::toVoice);
    if (exact.isPresent()) {
      return exact;
    }
    // Same language, another locale: the voice says which, so a fr-CA turn spoken in fr-FR is
    // reported as fr-FR rather than passed off as Québécois (ADR-076 — there is no Piper voice
    // for fr-CA today).
    String language = SpeechVoice.languageOf(locale);
    return repository.findByCategoryAndLocaleDefaultTrueOrderByLocaleAsc(SPEECH).stream()
        .filter(entity -> SpeechVoice.languageOf(entity.getLocale()).equals(language))
        .findFirst()
        .map(SpeechVoiceManagerImpl::toVoice);
  }

  private static SpeechVoice toVoice(CatalogModelEntity entity) {
    return new SpeechVoice(entity.getModelName(), entity.getLocale());
  }
}
