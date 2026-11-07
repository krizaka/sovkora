package com.krizaka.sovkora.tools.domain.model.law;

/**
 * The deterministic check of a rule: a type of the ruleset's closed set and its pattern.
 *
 * @param type {@code REQUIRED_PATTERN} or {@code FORBIDDEN_PATTERN} for a derived rule
 * @param pattern the regular expression, in the dialect the pack's worker runs
 */
public record RuleCheck(String type, String pattern) {}
