package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One import of the law corpus, manual ones included (job-service {@code law_import}). */
@Entity
@Table(name = "law_import")
public class LawImportEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "source_key", nullable = false)
  private String sourceKey;

  @Column(name = "mode", nullable = false)
  private String mode;

  @Column(name = "dataset_url", columnDefinition = "TEXT")
  private String datasetUrl;

  @Column(name = "dataset_etag", columnDefinition = "TEXT")
  private String datasetEtag;

  @Column(name = "dataset_sha256", length = 64)
  private String datasetSha256;

  @Column(name = "consolidated_to")
  private LocalDate consolidatedTo;

  @Column(name = "imported_by")
  private String importedBy;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "articles_new", nullable = false)
  private int articlesNew;

  @Column(name = "articles_changed", nullable = false)
  private int articlesChanged;

  @Column(name = "articles_unchanged", nullable = false)
  private int articlesUnchanged;

  @Column(name = "status", nullable = false)
  private String status;

  public LawImportEntity() {
    /* JPA requires a no-arg constructor */
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String getDatasetUrl() {
    return datasetUrl;
  }

  public void setDatasetUrl(String datasetUrl) {
    this.datasetUrl = datasetUrl;
  }

  public String getDatasetEtag() {
    return datasetEtag;
  }

  public void setDatasetEtag(String datasetEtag) {
    this.datasetEtag = datasetEtag;
  }

  public String getDatasetSha256() {
    return datasetSha256;
  }

  public void setDatasetSha256(String datasetSha256) {
    this.datasetSha256 = datasetSha256;
  }

  public LocalDate getConsolidatedTo() {
    return consolidatedTo;
  }

  public void setConsolidatedTo(LocalDate consolidatedTo) {
    this.consolidatedTo = consolidatedTo;
  }

  public String getImportedBy() {
    return importedBy;
  }

  public void setImportedBy(String importedBy) {
    this.importedBy = importedBy;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  public int getArticlesNew() {
    return articlesNew;
  }

  public void setArticlesNew(int articlesNew) {
    this.articlesNew = articlesNew;
  }

  public int getArticlesChanged() {
    return articlesChanged;
  }

  public void setArticlesChanged(int articlesChanged) {
    this.articlesChanged = articlesChanged;
  }

  public int getArticlesUnchanged() {
    return articlesUnchanged;
  }

  public void setArticlesUnchanged(int articlesUnchanged) {
    this.articlesUnchanged = articlesUnchanged;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getSourceKey() {
    return sourceKey;
  }

  public void setSourceKey(String sourceKey) {
    this.sourceKey = sourceKey;
  }
}
