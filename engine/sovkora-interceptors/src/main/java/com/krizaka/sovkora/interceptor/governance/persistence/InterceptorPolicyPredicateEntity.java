package com.krizaka.sovkora.interceptor.governance.persistence;

import com.krizaka.sovkora.interceptor.governance.PredicateOperator;
import jakarta.persistence.*;
import java.util.UUID;

/** JPA entity for the {@code interceptor_policy_predicate} table — individual predicate row. */
@Entity
@Table(name = "interceptor_policy_predicate")
class InterceptorPolicyPredicateEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "policy_id", nullable = false)
  private UUID policyId;

  @Column(name = "field", nullable = false)
  private String field;

  @Enumerated(EnumType.STRING)
  @Column(name = "operator", nullable = false)
  private PredicateOperator operator;

  @Column(name = "value", nullable = false)
  private String value;

  UUID getId() {
    return id;
  }

  UUID getPolicyId() {
    return policyId;
  }

  String getField() {
    return field;
  }

  PredicateOperator getOperator() {
    return operator;
  }

  String getValue() {
    return value;
  }
}
