package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** What research read, once per URL and content hash (ADR-087 §5). */
@Entity
@Table(name = "research_document")
public class ResearchDocumentEntity {

  @EmbeddedId private ResearchDocumentId id;

  @Column(name = "final_url")
  private String finalUrl;

  @Column(name = "fetched_at")
  private Instant fetchedAt;

  @Column(name = "http_status")
  private int httpStatus;

  @Column(name = "content_type")
  private String contentType;

  @Column(name = "bytes")
  private int bytes;

  @Column(name = "etag")
  private String etag;

  @Column(name = "last_modified")
  private String lastModified;

  @Column(name = "title")
  private String title;

  @Column(name = "text")
  private String text;

  @Column(name = "expires_at")
  private Instant expiresAt;

  public ResearchDocumentEntity() {
    /* JPA requires a no-arg constructor */
  }

  public ResearchDocumentId getId() {
    return id;
  }

  public void setId(ResearchDocumentId id) {
    this.id = id;
  }

  public String getFinalUrl() {
    return finalUrl;
  }

  public void setFinalUrl(String finalUrl) {
    this.finalUrl = finalUrl;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }

  public void setFetchedAt(Instant fetchedAt) {
    this.fetchedAt = fetchedAt;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  public void setHttpStatus(int httpStatus) {
    this.httpStatus = httpStatus;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public int getBytes() {
    return bytes;
  }

  public void setBytes(int bytes) {
    this.bytes = bytes;
  }

  public String getEtag() {
    return etag;
  }

  public void setEtag(String etag) {
    this.etag = etag;
  }

  public String getLastModified() {
    return lastModified;
  }

  public void setLastModified(String lastModified) {
    this.lastModified = lastModified;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }
}
