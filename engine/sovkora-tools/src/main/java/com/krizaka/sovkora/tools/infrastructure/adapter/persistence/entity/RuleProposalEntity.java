package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One proposed rule, one revision (job-service {@code rule_proposal}, ADR-089 §3). */
@Entity
@Table(
    name = "rule_proposal",
    uniqueConstraints =
        @UniqueConstraint(
            columnNames = {"jurisdiction", "kind_id", "rule_id", "proposal_revision"}))
public class RuleProposalEntity {

  @Id private UUID id;

  @Column(name = "jurisdiction", nullable = false, length = 10)
  private String jurisdiction;

  @Column(name = "kind_id", nullable = false, length = 80)
  private String kindId;

  @Column(name = "rule_id", nullable = false, length = 120)
  private String ruleId;

  @Column(name = "proposal_revision", nullable = false)
  private int proposalRevision;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rule", nullable = false, columnDefinition = "jsonb")
  private String rule;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "law", nullable = false, columnDefinition = "jsonb")
  private String law;

  @Column(name = "derivation_run_id")
  private UUID derivationRunId;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "vet_report", columnDefinition = "jsonb")
  private String vetReport;

  @Column(name = "reviewed_by")
  private String reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "decision_note")
  private String decisionNote;

  @Column(name = "packs_pr_url")
  private String packsPrUrl;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public RuleProposalEntity() {
    /* JPA requires a no-arg constructor */
  }

  public UUID getId() {
    return id;
  }

  public String getJurisdiction() {
    return jurisdiction;
  }

  public String getKindId() {
    return kindId;
  }

  public String getRuleId() {
    return ruleId;
  }

  public int getProposalRevision() {
    return proposalRevision;
  }

  public String getRule() {
    return rule;
  }

  public String getLaw() {
    return law;
  }

  public UUID getDerivationRunId() {
    return derivationRunId;
  }

  public String getStatus() {
    return status;
  }

  public String getVetReport() {
    return vetReport;
  }

  public String getReviewedBy() {
    return reviewedBy;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getDecisionNote() {
    return decisionNote;
  }

  public String getPacksPrUrl() {
    return packsPrUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
