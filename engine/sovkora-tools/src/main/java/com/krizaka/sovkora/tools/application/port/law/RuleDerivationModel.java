package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.tools.domain.model.law.ProposedRule;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The model port of stage 3 (ADR-089 §3, the {@code propose} step): given one article and the
 * verified claims on it, the model proposes rules in the shape of a ruleset rule, each with its
 * {@code law} block and a fixture pair written from the article's requirement.
 *
 * <p>It is the only place a derivation calls a model. The article and the claims reach the model as
 * data, never as instructions (ADR-087 §4); what it returns is a proposal that {@code vet} reads,
 * never a rule.
 */
public interface RuleDerivationModel {

  /**
   * The article handed to the model.
   *
   * @param instrument the instrument code
   * @param ref the article reference
   * @param language {@code fr} or {@code en}
   * @param contentHash SHA-256 of its text, the version a {@code law} block must cite
   * @param officialUrl the official address, the rule's {@code sourceUrl}
   * @param text the article text
   */
  record Article(
      String instrument,
      String ref,
      String language,
      String contentHash,
      String officialUrl,
      String text) {}

  /**
   * One call.
   *
   * @param jurisdiction the jurisdiction
   * @param kindId the kind of document the rules are for
   * @param language the language the rules are written in
   * @param asOf the day the article is in force
   * @param article the article
   * @param claims the verified claims on it, the only facts the rules may rest on
   * @param rulesLeft how many rules the derivation may still propose
   */
  record Prompt(
      String jurisdiction,
      String kindId,
      String language,
      LocalDate asOf,
      Article article,
      List<Claim> claims,
      int rulesLeft) {

    /** Copies the claims. */
    public Prompt {
      Objects.requireNonNull(article, "article");
      claims = claims == null ? List.of() : List.copyOf(claims);
    }
  }

  /**
   * What one call answered and what it cost.
   *
   * @param rules the proposed rules, parsed from the answer; empty when it proposed none
   * @param credits the credits the call was charged, never more than {@link #callCeiling()}
   */
  record Answer(List<ProposedRule> rules, long credits) {

    /** Copies the rules and checks the charge. */
    public Answer {
      rules = rules == null ? List.of() : List.copyOf(rules);
      if (credits < 0) {
        throw new IllegalArgumentException("credits cannot be negative");
      }
    }
  }

  /**
   * The most one call can be charged: its output is capped, so its price is bounded. A derivation
   * makes a call only when this still fits in what is left of {@code law.derive.max-credits}.
   *
   * @return the ceiling in credits, at least 1
   */
  long callCeiling();

  /**
   * Asks for rules on one article.
   *
   * @param prompt the article, its verified claims and the day
   * @return the proposed rules and the charge
   */
  Answer propose(Prompt prompt);
}
