package com.krizaka.sovkora.tools.domain.model.law;

import java.util.List;

/**
 * The fixture pair of a rule (ADR-072): fragments written from the article's requirement, that the
 * rule must PASS and FAIL.
 *
 * @param pass the fragments the rule must pass
 * @param fail the fragments the rule must fail
 */
public record RuleFixtures(List<String> pass, List<String> fail) {

  /** Copies the lists; a missing side is empty. */
  public RuleFixtures {
    pass = pass == null ? List.of() : List.copyOf(pass);
    fail = fail == null ? List.of() : List.copyOf(fail);
  }
}
