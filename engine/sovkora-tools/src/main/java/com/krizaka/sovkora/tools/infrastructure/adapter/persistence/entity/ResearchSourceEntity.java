package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A research source as the administrator declared it (ADR-087); read-only here, managed through the
 * admin API.
 */
@Entity
@Table(name = "research_source")
public class ResearchSourceEntity {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "source_key")
  private String sourceKey;

  @Column(name = "kind")
  private String kind;

  @Column(name = "provider")
  private String provider;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "allow_patterns")
  private String[] allowPatterns;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "deny_patterns")
  private String[] denyPatterns;

  @Column(name = "allow_http")
  private boolean allowHttp;

  @Column(name = "freshness_seconds")
  private int freshnessSeconds;

  @Column(name = "rate_per_minute")
  private int ratePerMinute;

  @Column(name = "enabled")
  private boolean enabled;

  public ResearchSourceEntity() {
    /* JPA requires a no-arg constructor */
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getSourceKey() {
    return sourceKey;
  }

  public void setSourceKey(String sourceKey) {
    this.sourceKey = sourceKey;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String[] getAllowPatterns() {
    return allowPatterns;
  }

  public void setAllowPatterns(String[] allowPatterns) {
    this.allowPatterns = allowPatterns;
  }

  public String[] getDenyPatterns() {
    return denyPatterns;
  }

  public void setDenyPatterns(String[] denyPatterns) {
    this.denyPatterns = denyPatterns;
  }

  public boolean getAllowHttp() {
    return allowHttp;
  }

  public void setAllowHttp(boolean allowHttp) {
    this.allowHttp = allowHttp;
  }

  public int getFreshnessSeconds() {
    return freshnessSeconds;
  }

  public void setFreshnessSeconds(int freshnessSeconds) {
    this.freshnessSeconds = freshnessSeconds;
  }

  public int getRatePerMinute() {
    return ratePerMinute;
  }

  public void setRatePerMinute(int ratePerMinute) {
    this.ratePerMinute = ratePerMinute;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
