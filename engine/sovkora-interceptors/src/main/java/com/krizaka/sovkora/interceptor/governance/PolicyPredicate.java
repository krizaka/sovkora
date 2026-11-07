package com.krizaka.sovkora.interceptor.governance;

/**
 * Policy predicate — a single data-driven condition evaluated against pipeline context.
 *
 * <p>Maps to the JSON structure:
 *
 * <pre>
 * { "field": "context.budgetRemaining", "operator": "LESS_THAN", "value": "0.50" }
 * </pre>
 *
 * @param field dot-notation path to the context field (e.g., "context.budgetRemaining").
 * @param operator the compiled {@link PredicateOperator} to apply.
 * @param value the expected value for comparison.
 */
public record PolicyPredicate(String field, PredicateOperator operator, String value) {

  public PolicyPredicate {
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("PolicyPredicate requires a non-blank field");
    }
    if (operator == null) {
      throw new IllegalArgumentException("PolicyPredicate requires a non-null operator");
    }
    if (value == null) {
      throw new IllegalArgumentException("PolicyPredicate requires a non-null value");
    }
  }
}
