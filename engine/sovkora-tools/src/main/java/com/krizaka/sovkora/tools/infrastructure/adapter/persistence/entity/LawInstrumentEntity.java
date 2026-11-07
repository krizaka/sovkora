package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * One act or regulation of the law corpus (job-service {@code law_instrument}). Created by the
 * first import of the instrument; the administrator edits titles and licence afterwards.
 */
@Entity
@Table(
    name = "law_instrument",
    uniqueConstraints = @UniqueConstraint(columnNames = {"jurisdiction", "code"}))
public class LawInstrumentEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "jurisdiction", nullable = false)
  private String jurisdiction;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "title_fr", columnDefinition = "TEXT")
  private String titleFr;

  @Column(name = "title_en", columnDefinition = "TEXT")
  private String titleEn;

  @Column(name = "official_url_template", nullable = false, columnDefinition = "TEXT")
  private String officialUrlTemplate;

  @Column(name = "source_key", nullable = false)
  private String sourceKey;

  @Column(name = "licence", columnDefinition = "TEXT")
  private String licence;

  @Column(name = "access_note", columnDefinition = "TEXT")
  private String accessNote;

  public LawInstrumentEntity() {
    /* JPA requires a no-arg constructor */
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getJurisdiction() {
    return jurisdiction;
  }

  public void setJurisdiction(String jurisdiction) {
    this.jurisdiction = jurisdiction;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getTitleFr() {
    return titleFr;
  }

  public void setTitleFr(String titleFr) {
    this.titleFr = titleFr;
  }

  public String getTitleEn() {
    return titleEn;
  }

  public void setTitleEn(String titleEn) {
    this.titleEn = titleEn;
  }

  public String getOfficialUrlTemplate() {
    return officialUrlTemplate;
  }

  public void setOfficialUrlTemplate(String officialUrlTemplate) {
    this.officialUrlTemplate = officialUrlTemplate;
  }

  public String getSourceKey() {
    return sourceKey;
  }

  public void setSourceKey(String sourceKey) {
    this.sourceKey = sourceKey;
  }

  public String getLicence() {
    return licence;
  }

  public void setLicence(String licence) {
    this.licence = licence;
  }

  public String getAccessNote() {
    return accessNote;
  }

  public void setAccessNote(String accessNote) {
    this.accessNote = accessNote;
  }
}
