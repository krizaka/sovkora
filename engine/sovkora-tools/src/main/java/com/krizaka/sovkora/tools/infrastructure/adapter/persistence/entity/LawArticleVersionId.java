package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The key of a version of an article: the instrument, the reference, the language and the day it
 * took effect. The key is the period, not the text: a reverted amendment brings back an earlier
 * text and still starts a new period, so it is a new row (ADR-089, krizaka/sovkora#401).
 */
@Embeddable
public class LawArticleVersionId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "instrument_id")
  private UUID instrumentId;

  @Column(name = "ref", length = 40)
  private String ref;

  @Column(name = "language", length = 2)
  private String language;

  @Column(name = "in_force_from")
  private LocalDate inForceFrom;

  public LawArticleVersionId() {
    /* JPA requires a no-arg constructor */
  }

  public LawArticleVersionId(
      UUID instrumentId, String ref, String language, LocalDate inForceFrom) {
    this.instrumentId = instrumentId;
    this.ref = ref;
    this.language = language;
    this.inForceFrom = inForceFrom;
  }

  public UUID getInstrumentId() {
    return instrumentId;
  }

  public String getRef() {
    return ref;
  }

  public String getLanguage() {
    return language;
  }

  public LocalDate getInForceFrom() {
    return inForceFrom;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof LawArticleVersionId that
        && Objects.equals(instrumentId, that.instrumentId)
        && Objects.equals(ref, that.ref)
        && Objects.equals(language, that.language)
        && Objects.equals(inForceFrom, that.inForceFrom);
  }

  @Override
  public int hashCode() {
    return Objects.hash(instrumentId, ref, language, inForceFrom);
  }
}
