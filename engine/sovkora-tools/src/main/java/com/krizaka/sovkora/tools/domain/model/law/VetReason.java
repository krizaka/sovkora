package com.krizaka.sovkora.tools.domain.model.law;

/**
 * Why {@code vet} rejects a proposed rule (ADR-089 §3). Each reason is decided by code, never by a
 * model; each has its planted proposal in the tests.
 */
public enum VetReason {
  /**
   * The rule is not in the shape of a ruleset rule: a part is missing, the method or check type is
   * not one a derivation may propose, a {@code JUDGMENT} rule is not {@code ADVISORY}, or the rule
   * id is used twice. The other checks need these parts, so they do not run.
   */
  SCHEMA_INVALID,
  /** A pattern does not compile, cannot run the same way in the pack's worker, or can backtrack. */
  REGEX_INVALID,
  /** A pattern matches the empty string, so it finds something in every document. */
  REGEX_MATCHES_EMPTY,
  /** A {@code law} block's quote is not a passage of the article text it names. */
  QUOTE_NOT_IN_ARTICLE,
  /**
   * A number, duration or time the rule states (24 heures, 9 h, trois mois) is in none of its
   * quotes: the support check of ADR-087.
   */
  UNSUPPORTED_NUMBER,
  /** The rule cannot tell its own fixture pair apart (ADR-072). */
  FIXTURES_INDISTINCT,
  /** A {@code FORBIDDEN_PATTERN} or {@code JUDGMENT} rule declares no {@code subject} (#127). */
  SUBJECT_MISSING,
  /**
   * The rule cites an article the corpus holds no version of on {@code asOf}, or a version (content
   * hash) that is not the one in force then.
   */
  LAW_VERSION_UNKNOWN,
  /** The derivation already has {@code law.derive.max-rules-per-kind} rules before this one. */
  OVER_RULE_LIMIT
}
