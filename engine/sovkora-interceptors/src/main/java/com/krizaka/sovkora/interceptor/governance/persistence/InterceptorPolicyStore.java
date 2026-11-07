package com.krizaka.sovkora.interceptor.governance.persistence;

import com.krizaka.sovkora.interceptor.governance.InterceptorPolicy;
import java.util.List;

/**
 * Public persistence facade for interceptor policies — loads them as domain {@link
 * InterceptorPolicy} objects, keeping the JPA entities, repository and mapper package-private to
 * this sub-package.
 */
public final class InterceptorPolicyStore {

  private final InterceptorPolicyRepository repository;
  private final InterceptorPolicyMapper mapper = new InterceptorPolicyMapper();

  public InterceptorPolicyStore(InterceptorPolicyRepository repository) {
    this.repository = repository;
  }

  /** Loads all policies ordered by execution order, as domain objects. */
  public List<InterceptorPolicy> findAllOrdered() {
    return repository.findAllByOrderByExecutionOrderAsc().stream().map(mapper::toDomain).toList();
  }
}
