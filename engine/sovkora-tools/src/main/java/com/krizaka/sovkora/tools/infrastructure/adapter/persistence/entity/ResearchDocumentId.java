package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * The key of a research document: the source that authorised the read, the URL as requested, and
 * the hash of what was read there. A document never answers through another source (ADR-087 §5).
 */
@Embeddable
public class ResearchDocumentId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "source_id")
  private UUID sourceId;

  @Column(name = "url")
  private String url;

  @Column(name = "content_hash")
  private String contentHash;

  public ResearchDocumentId() {
    /* JPA requires a no-arg constructor */
  }

  public ResearchDocumentId(UUID sourceId, String url, String contentHash) {
    this.sourceId = sourceId;
    this.url = url;
    this.contentHash = contentHash;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public String getUrl() {
    return url;
  }

  public String getContentHash() {
    return contentHash;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ResearchDocumentId that
        && Objects.equals(sourceId, that.sourceId)
        && Objects.equals(url, that.url)
        && Objects.equals(contentHash, that.contentHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceId, url, contentHash);
  }
}
