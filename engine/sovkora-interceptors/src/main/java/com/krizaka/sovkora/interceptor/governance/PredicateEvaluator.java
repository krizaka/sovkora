package com.krizaka.sovkora.interceptor.governance;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Predicate evaluator — evaluates a list of {@link PolicyPredicate} against a context map.
 *
 * <p>Resilience guardrail: evaluation failure = SKIP interceptor + log correlationId. Exceptions
 * are NEVER propagated to the caller.
 */
final class PredicateEvaluator {

  private static final Logger log = LoggerFactory.getLogger(PredicateEvaluator.class);

  private PredicateEvaluator() {}

  /**
   * Evaluate all predicates against the provided context.
   *
   * @param predicates the list of predicates to evaluate (ALL must pass).
   * @param context the resolved context map (dot-notation keys → string values).
   * @param correlationId the correlation ID for audit logging.
   * @return true if ALL predicates pass; false on any failure (including exceptions).
   */
  static boolean evaluateAll(
      List<PolicyPredicate> predicates, Map<String, Object> context, String correlationId) {
    if (predicates == null || predicates.isEmpty()) {
      return true;
    }

    for (PolicyPredicate predicate : predicates) {
      try {
        Object rawValue = context.get(predicate.field());
        String fieldValue = rawValue != null ? rawValue.toString() : null;

        if (!predicate.operator().evaluate(fieldValue, predicate.value())) {
          log.debug(
              "Predicate failed for correlationId={}: field={}, operator={}, expected={}, actual={}",
              correlationId,
              predicate.field(),
              predicate.operator(),
              predicate.value(),
              fieldValue);
          return false;
        }
      } catch (Exception e) {
        // Resilience guardrail: SKIP interceptor on evaluation failure, never propagate
        log.warn(
            "Predicate evaluation failed for correlationId={}, field={}: {}. Skipping interceptor.",
            correlationId,
            predicate.field(),
            e.getMessage());
        return false;
      }
    }
    return true;
  }
}
