package com.krizaka.sovkora.interceptor.governance.persistence;

import com.krizaka.sovkora.interceptor.governance.InterceptorPolicy;
import com.krizaka.sovkora.interceptor.governance.PolicyPredicate;

/**
 * Mapper for converting JPA entities to domain records. Package-private per ERR-107 — static
 * methods, no setter chains.
 */
final class InterceptorPolicyMapper {

  InterceptorPolicy toDomain(InterceptorPolicyEntity entity) {
    var predicates =
        entity.getPredicates().stream()
            .map(p -> new PolicyPredicate(p.getField(), p.getOperator(), p.getValue()))
            .toList();

    return new InterceptorPolicy(
        entity.getId(),
        entity.getInterceptorName(),
        entity.getExecutionOrder(),
        entity.isEnabled(),
        predicates);
  }
}
