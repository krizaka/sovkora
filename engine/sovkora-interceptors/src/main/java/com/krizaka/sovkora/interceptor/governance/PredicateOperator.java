package com.krizaka.sovkora.interceptor.governance;

/**
 * Compiled predicate operator enum — evaluates pure data predicates safely.
 *
 * <p>Security invariant: ZERO execution scripts or SpEL in the database. All evaluation logic is
 * compiled into this enum. Predicates are pure data mapped to the JSON structure:
 *
 * <pre>
 * { "field": "context.budgetRemaining", "operator": "LESS_THAN", "value": 0.50 }
 * </pre>
 */
public enum PredicateOperator {
  EQUALS {
    @Override
    public boolean evaluate(String fieldValue, String predicateValue) {
      return fieldValue != null && fieldValue.equals(predicateValue);
    }
  },
  NOT_EQUALS {
    @Override
    public boolean evaluate(String fieldValue, String predicateValue) {
      return fieldValue == null || !fieldValue.equals(predicateValue);
    }
  },
  GREATER_THAN {
    @Override
    public boolean evaluate(String fieldValue, String predicateValue) {
      return compareNumeric(fieldValue, predicateValue) > 0;
    }
  },
  LESS_THAN {
    @Override
    public boolean evaluate(String fieldValue, String predicateValue) {
      return compareNumeric(fieldValue, predicateValue) < 0;
    }
  },
  GREATER_THAN_OR_EQUAL {
    @Override
    public boolean evaluate(String fieldValue, String predicateValue) {
      return compareNumeric(fieldValue, predicateValue) >= 0;
    }
  },
  LESS_THAN_OR_EQUAL {
    @Override
    public boolean evaluate(String fieldValue, String predicateValue) {
      return compareNumeric(fieldValue, predicateValue) <= 0;
    }
  },
  CONTAINS {
    @Override
    public boolean evaluate(String fieldValue, String predicateValue) {
      return fieldValue != null && predicateValue != null && fieldValue.contains(predicateValue);
    }
  },
  IN {
    @Override
    public boolean evaluate(String fieldValue, String predicateValue) {
      if (fieldValue == null || predicateValue == null) {
        return false;
      }
      for (String item : predicateValue.split(",")) {
        if (item.trim().equals(fieldValue)) {
          return true;
        }
      }
      return false;
    }
  };

  /**
   * Evaluate the predicate against the given field and predicate values.
   *
   * @param fieldValue the resolved field value from the context
   * @param predicateValue the expected value from the policy predicate
   * @return true if the predicate condition is satisfied
   */
  public abstract boolean evaluate(String fieldValue, String predicateValue);

  private static int compareNumeric(String fieldValue, String predicateValue) {
    if (fieldValue == null || predicateValue == null) {
      return 0;
    }
    return Double.compare(Double.parseDouble(fieldValue), Double.parseDouble(predicateValue));
  }
}
