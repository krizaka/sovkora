package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** JPA Entity mapping the {@code platform_mcp_servers} database table. */
@Entity
@Table(name = "platform_mcp_servers")
public class PlatformMcpServerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "label", nullable = false, length = 255)
  private String label;

  @Column(name = "transport_type", nullable = false, length = 50)
  private String transportType;

  @Column(name = "url", length = 1000)
  private String url;

  @Column(name = "command", length = 1000)
  private String command;

  @Column(name = "args", length = 2000)
  private String args;

  @Column(name = "auth_token", columnDefinition = "TEXT")
  private String authToken;

  @Column(name = "enabled")
  private Boolean enabled = true;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  public PlatformMcpServerEntity() {
    /* JPA requires no-arg constructor */
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getTransportType() {
    return transportType;
  }

  public void setTransportType(String transportType) {
    this.transportType = transportType;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getCommand() {
    return command;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public String getArgs() {
    return args;
  }

  public void setArgs(String args) {
    this.args = args;
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
