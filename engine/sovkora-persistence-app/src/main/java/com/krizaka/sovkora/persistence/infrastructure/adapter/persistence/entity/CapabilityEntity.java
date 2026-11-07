package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA Entity mapping the operation-graph capability registry table.
 *
 * <p>Single source of truth for capability blueprints (label/icon/routing metadata) and their
 * enabled state, superseding the former {@code orazaka_feature_flags} table and the {@code
 * orazaka.features.*} yaml block.
 */
@Entity
@Table(name = "orazaka_capabilities")
public class CapabilityEntity {

  @Id
  @Column(name = "feature_key")
  private String featureKey;

  @Column(name = "handler_key", nullable = false)
  private String handlerKey;

  /** ADR-037 (S1): where this capability's work is published. Data, never a substring heuristic. */
  @Column(name = "routing_key", nullable = false)
  private String routingKey;

  @Column(name = "billable_unit")
  private String billableUnit;

  /** Which lane this capability's work waits in — INTERACTIVE or BATCH (ADR-067). */
  @Column(name = "latency_class")
  private String latencyClass;

  /** ADR-038: what this bills as, so the money path reads a row instead of a substring chain. */
  @Column(name = "billable_capability", nullable = false)
  private String billableCapability;

  /**
   * The input half of the capability contract (ADR-069): what a caller may pass, as a JSON Schema.
   *
   * <p>{@code jsonb} and not {@code text} on purpose — Postgres refuses a malformed schema at
   * insert, which is the difference between a contract and a string that looks like one. What it
   * replaces, {@code payload_template}, was {@code TEXT} and carried {@code ${placeholders}} no
   * database and no rule could read.
   */
  @Column(name = "input_schema", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String inputSchema;

  /** The output half: the fields a successful execution publishes, with their types (ADR-069). */
  @Column(name = "output_schema", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String outputSchema;

  @Column(name = "is_enabled", nullable = false)
  private Boolean isEnabled;

  public String getFeatureKey() {
    return featureKey;
  }

  public void setFeatureKey(String featureKey) {
    this.featureKey = featureKey;
  }

  public String getHandlerKey() {
    return handlerKey;
  }

  public void setHandlerKey(String handlerKey) {
    this.handlerKey = handlerKey;
  }

  public String getRoutingKey() {
    return routingKey;
  }

  public void setRoutingKey(String routingKey) {
    this.routingKey = routingKey;
  }

  public String getBillableUnit() {
    return billableUnit;
  }

  public String getLatencyClass() {
    return latencyClass;
  }

  public void setLatencyClass(String latencyClass) {
    this.latencyClass = latencyClass;
  }

  public String getInputSchema() {
    return inputSchema;
  }

  public void setInputSchema(String inputSchema) {
    this.inputSchema = inputSchema;
  }

  public String getOutputSchema() {
    return outputSchema;
  }

  public void setOutputSchema(String outputSchema) {
    this.outputSchema = outputSchema;
  }

  public void setBillableUnit(String billableUnit) {
    this.billableUnit = billableUnit;
  }

  public String getBillableCapability() {
    return billableCapability;
  }

  public void setBillableCapability(String billableCapability) {
    this.billableCapability = billableCapability;
  }

  public Boolean getIsEnabled() {
    return isEnabled;
  }

  public void setIsEnabled(Boolean isEnabled) {
    this.isEnabled = isEnabled;
  }

  /**
   * The endpoint rule was enforced here too, at the ORM boundary, because a row can reach the
   * database without passing through {@code CapabilityDeclaration} — which is how this mapping came
   * to say {@code nullable = false} while the contract said otherwise (ADR-057 §2). It is gone with
   * {@code uri_path} and {@code http_method} (ADR-069 §5): there is no endpoint on a capability any
   * more, so there is no half of one to refuse.
   */
}
