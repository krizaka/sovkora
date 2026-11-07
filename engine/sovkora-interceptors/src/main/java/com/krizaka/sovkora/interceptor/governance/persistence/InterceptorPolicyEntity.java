package com.krizaka.sovkora.interceptor.governance.persistence;

import jakarta.persistence.*;
import java.util.List;
import java.util.UUID;

/** JPA entity for the {@code interceptor_policy} table — data-driven interceptor governance. */
@Entity
@Table(name = "interceptor_policy")
class InterceptorPolicyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "interceptor_name", nullable = false)
  private String interceptorName;

  @Column(name = "execution_order", nullable = false)
  private int executionOrder;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "policy_id")
  private List<InterceptorPolicyPredicateEntity> predicates;

  UUID getId() {
    return id;
  }

  String getInterceptorName() {
    return interceptorName;
  }

  int getExecutionOrder() {
    return executionOrder;
  }

  boolean isEnabled() {
    return enabled;
  }

  List<InterceptorPolicyPredicateEntity> getPredicates() {
    return predicates;
  }
}
