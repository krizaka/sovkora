package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One dated version of an article in one language (job-service {@code law_article_version}). */
@Entity
@Table(name = "law_article_version")
public class LawArticleVersionEntity {

  @EmbeddedId private LawArticleVersionId id;

  @Column(name = "in_force_to")
  private LocalDate inForceTo;

  @Column(name = "heading_path", columnDefinition = "TEXT")
  private String headingPath;

  @Column(name = "text", nullable = false, columnDefinition = "TEXT")
  private String text;

  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Column(name = "history_note", columnDefinition = "TEXT")
  private String historyNote;

  @Column(name = "import_id", nullable = false)
  private UUID importId;

  @Column(name = "first_seen_at", nullable = false)
  private Instant firstSeenAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  public LawArticleVersionEntity() {
    /* JPA requires a no-arg constructor */
  }

  public LawArticleVersionId getId() {
    return id;
  }

  public void setId(LawArticleVersionId id) {
    this.id = id;
  }

  public LocalDate getInForceTo() {
    return inForceTo;
  }

  public void setInForceTo(LocalDate inForceTo) {
    this.inForceTo = inForceTo;
  }

  public String getHeadingPath() {
    return headingPath;
  }

  public void setHeadingPath(String headingPath) {
    this.headingPath = headingPath;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  public String getHistoryNote() {
    return historyNote;
  }

  public void setHistoryNote(String historyNote) {
    this.historyNote = historyNote;
  }

  public UUID getImportId() {
    return importId;
  }

  public void setImportId(UUID importId) {
    this.importId = importId;
  }

  public Instant getFirstSeenAt() {
    return firstSeenAt;
  }

  public void setFirstSeenAt(Instant firstSeenAt) {
    this.firstSeenAt = firstSeenAt;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(Instant lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }
}
