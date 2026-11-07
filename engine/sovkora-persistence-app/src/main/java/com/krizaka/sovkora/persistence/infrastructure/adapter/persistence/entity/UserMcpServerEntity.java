package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** JPA Entity mapping the {@code user_mcp_servers} database table. */
@Entity
@Table(name = "user_mcp_servers")
public class UserMcpServerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "user_id", nullable = false, length = 255)
  private String userId;

  @Column(name = "label", nullable = false, length = 255)
  private String label;

  @Column(name = "url", nullable = false, length = 1000)
  private String url;

  @Column(name = "auth_token", columnDefinition = "TEXT")
  private String authToken;

  @Column(name = "enabled")
  private Boolean enabled = true;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  public UserMcpServerEntity() {
    /* JPA requires no-arg constructor */
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getAuthToken() {
    return authToken;
  }

  public void setAuthToken(String authToken) {
    this.authToken = authToken;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
