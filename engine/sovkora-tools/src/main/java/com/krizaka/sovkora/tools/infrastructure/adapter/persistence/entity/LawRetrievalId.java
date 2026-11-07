package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** The key of a cached retrieval: jurisdiction, kind and as-of bucket. */
@Embeddable
public class LawRetrievalId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "jurisdiction", length = 10)
  private String jurisdiction;

  @Column(name = "kind_id", length = 80)
  private String kindId;

  @Column(name = "language", length = 2)
  private String language;

  @Column(name = "as_of_bucket")
  private LocalDate asOfBucket;

  public LawRetrievalId() {
    /* JPA requires a no-arg constructor */
  }

  public LawRetrievalId(String jurisdiction, String kindId, String language, LocalDate asOfBucket) {
    this.jurisdiction = jurisdiction;
    this.kindId = kindId;
    this.language = language;
    this.asOfBucket = asOfBucket;
  }

  public String getJurisdiction() {
    return jurisdiction;
  }

  public String getKindId() {
    return kindId;
  }

  public String getLanguage() {
    return language;
  }

  public LocalDate getAsOfBucket() {
    return asOfBucket;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof LawRetrievalId other
        && Objects.equals(jurisdiction, other.jurisdiction)
        && Objects.equals(kindId, other.kindId)
        && Objects.equals(language, other.language)
        && Objects.equals(asOfBucket, other.asOfBucket);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jurisdiction, kindId, language, asOfBucket);
  }
}
