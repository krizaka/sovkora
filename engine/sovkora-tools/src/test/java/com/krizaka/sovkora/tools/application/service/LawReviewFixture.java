package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.domain.model.law.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * The review queue of the visit notice in memory: art. 1931 to 1933 in the corpus as the official
 * XML gives them, and the recorded proposals of their derivation, vetted and recorded.
 */
final class LawReviewFixture {

  static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
  static final String KIND = "qc-visit-notice";
  static final UUID RUN = UUID.fromString("00000000-0000-4000-8000-000000000089");
  static final String ERIC = "eric-admin";

  final InMemoryLawCorpus corpus = new InMemoryLawCorpus();
  final FixedLawSettings settings = new FixedLawSettings();
  final InMemoryRuleProposals store = new InMemoryRuleProposals();
  final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  final UUID importId = UUID.fromString("00000000-0000-4000-8000-000000001931");
  final RuleProposalService proposals = new RuleProposalService(store, clock);
  final LawStatuteLookupService lookups = new LawStatuteLookupService(corpus, settings, clock);

  LawReviewFixture() {
    for (LawArticle article : DerivationFixtures.visitArticles()) {
      corpus.add(importId, "donnees-quebec", article, NOW);
    }
  }

  /** Records the derivation's proposals for art. 1931 and 1932; they are {@code PROPOSED}. */
  List<RuleProposal> recordVisitNotice() {
    List<ProposedRule> rules =
        new java.util.ArrayList<>(DerivationFixtures.visitAnswers().get("1931"));
    rules.addAll(DerivationFixtures.visitAnswers().get("1932"));
    return proposals.record(
        "QC",
        KIND,
        RUN,
        new RuleProposalVet(DerivationFixtures.visitCorpus(), DerivationFixtures.NOTICE_DAY, 30)
            .vet(rules));
  }

  /** Records and approves them all, as Eric does in the console. */
  List<RuleProposal> approvedVisitNotice() {
    return recordVisitNotice().stream()
        .peek(p -> assertProposed(p.status()))
        .map(
            p ->
                proposals.approve(
                    p.id(), ERIC, "read beside art. " + p.rule().law().getFirst().ref()))
        .toList();
  }

  private static void assertProposed(ProposalStatus status) {
    if (status != ProposalStatus.PROPOSED) {
      throw new AssertionError("the recorded visit-notice proposals pass vet");
    }
  }

  /** A copy of a rule with another title. */
  static ProposedRule retitled(ProposedRule r, String title) {
    return new ProposedRule(
        r.ruleId(),
        title,
        r.severity(),
        r.method(),
        r.subject(),
        r.check(),
        r.question(),
        r.citation(),
        r.sourceUrl(),
        r.onPass(),
        r.onFail(),
        r.fixtures(),
        r.law());
  }

  /** A copy of a rule whose quote is not in its article. */
  static ProposedRule misquoted(ProposedRule r) {
    LawBlock first = r.law().getFirst();
    return new ProposedRule(
        r.ruleId(),
        r.title(),
        r.severity(),
        r.method(),
        r.subject(),
        r.check(),
        r.question(),
        r.citation(),
        r.sourceUrl(),
        r.onPass(),
        r.onFail(),
        r.fixtures(),
        List.of(
            new LawBlock(
                first.instrument(),
                first.ref(),
                first.language(),
                first.contentHash(),
                "le locateur peut entrer à toute heure sans avis")));
  }

  /** A copy of a rule whose quote is longer than a ruleset holds. */
  static ProposedRule longQuote(ProposedRule r) {
    LawBlock first = r.law().getFirst();
    return new ProposedRule(
        r.ruleId(),
        r.title(),
        r.severity(),
        r.method(),
        r.subject(),
        r.check(),
        r.question(),
        r.citation(),
        r.sourceUrl(),
        r.onPass(),
        r.onFail(),
        r.fixtures(),
        List.of(
            new LawBlock(
                first.instrument(),
                first.ref(),
                first.language(),
                first.contentHash(),
                first.quote() + " " + "x".repeat(301))));
  }
}
