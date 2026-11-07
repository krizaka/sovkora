package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** The key of {@code kind_demand}: a jurisdiction and a kind (or {@code UNKNOWN}). */
@Embeddable
public class KindDemandId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "jurisdiction", length = 10)
  private String jurisdiction;

  @Column(name = "kind_id", length = 80)
  private String kindId;

  public KindDemandId() {
    /* JPA requires a no-arg constructor */
  }

  public KindDemandId(String jurisdiction, String kindId) {
    this.jurisdiction = jurisdiction;
    this.kindId = kindId;
  }

  public String getJurisdiction() {
    return jurisdiction;
  }

  public String getKindId() {
    return kindId;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof KindDemandId other
        && Objects.equals(jurisdiction, other.jurisdiction)
        && Objects.equals(kindId, other.kindId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jurisdiction, kindId);
  }
}
