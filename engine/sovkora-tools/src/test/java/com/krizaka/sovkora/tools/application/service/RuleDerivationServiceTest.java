package com.krizaka.sovkora.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.domain.model.research.Citation;
import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.core.domain.model.research.ClaimStatus;
import com.krizaka.sovkora.core.domain.model.research.GroundedAnswer;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.application.port.law.GroundedClaimsModel;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import com.krizaka.sovkora.tools.application.port.law.RuleDerivationModel;
import com.krizaka.sovkora.tools.domain.model.law.*;
import com.krizaka.sovkora.tools.domain.model.law.RuleDerivationResult.Outcome;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stage 3 of ADR-089 end to end in the engine: the visit notice's law retrieved from the corpus
 * (art. 1931 to 1933 as the official XML gives them), one recorded answer of the derivation model
 * per article, {@code vet}, and {@code rule_proposal}. No model is called: both model ports are
 * scripted.
 */
class RuleDerivationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
  private static final UUID RUN = UUID.randomUUID();
  private static final String KIND = "qc-visit-notice";

  private final InMemoryLawCorpus corpus = new InMemoryLawCorpus();
  private final InMemoryResearchStore research = new InMemoryResearchStore();
  private final InMemoryLawRetrievals cache = new InMemoryLawRetrievals();
  private final FixedLawSettings settings = new FixedLawSettings();
  private final InMemoryRuleProposals store = new InMemoryRuleProposals();
  private final GroundedModel grounded = new GroundedModel();
  private final RecordedDerivationModel model = new RecordedDerivationModel();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private LawRetrievalService retrievals;
  private RuleDerivationService service;

  @BeforeEach
  void setUp() {
    research.declare("law-corpus", "law-corpus", true);
    UUID importId = UUID.randomUUID();
    for (LawArticle article : DerivationFixtures.visitArticles()) {
      corpus.add(importId, "donnees-quebec", article, NOW);
    }
    retrievals =
        new LawRetrievalService(
            settings,
            research,
            new LawCorpusResearchService(corpus, research, clock),
            cache,
            grounded,
            new CitationVerificationService(research, new SeededConfig()),
            new FreeMeter(),
            clock);
    service =
        new RuleDerivationService(
            settings,
            retrievals,
            new LawStatuteLookupService(corpus, settings, clock),
            model,
            new RuleProposalService(store, clock));
  }

  private static LawRetrievalRequest visitNotice() {
    return new LawRetrievalRequest(
        "QC",
        KIND,
        "Quels articles régissent un avis de visite d'un logement au Québec ?",
        "fr",
        DerivationFixtures.NOTICE_DAY,
        List.of(new LawTargetRef("CCQ-1991", List.of("1931", "1932", "1933"))),
        RUN,
        "actor-eric");
  }

  @Test
  @DisplayName("the golden case: the visit notice's proposals pass vet and cite 1931 to 1933 only")
  void the_visit_notice_derivation_proposes_rules_that_pass_vet() {
    RuleDerivationResult result = service.derive(visitNotice());

    assertThat(result.outcome()).isEqualTo(Outcome.DERIVED);
    assertThat(result.retrieval()).isEqualTo(LawRetrievalResult.Outcome.RETRIEVED);
    assertThat(result.proposals())
        .extracting(RuleProposal::ruleId)
        .containsExactly("QC-VISIT-001", "QC-VISIT-002", "QC-VISIT-003", "QC-VISIT-004");
    assertThat(result.proposals())
        .allSatisfy(
            p -> {
              assertThat(p.status()).as(p.ruleId()).isEqualTo(ProposalStatus.PROPOSED);
              assertThat(p.vetReport().findings()).as(p.ruleId()).isEmpty();
              assertThat(p.proposalRevision()).isEqualTo(1);
              assertThat(p.derivationRunId()).isEqualTo(RUN);
              assertThat(p.jurisdiction()).isEqualTo("QC");
              assertThat(p.kindId()).isEqualTo(KIND);
            });
    assertThat(result.proposals())
        .flatExtracting(p -> p.rule().law())
        .extracting(LawBlock::ref)
        .containsOnly("1931", "1932", "1933");
    // One call per article, each handed its own article and only the verified claims on it.
    assertThat(model.prompts)
        .extracting(p -> p.article().ref())
        .containsExactly("1931", "1932", "1933");
    assertThat(model.prompts)
        .allSatisfy(
            p -> {
              assertThat(p.claims()).isNotEmpty().allMatch(c -> c.status() == ClaimStatus.VERIFIED);
              assertThat(p.article().text()).startsWith(p.article().ref() + " ");
              assertThat(p.asOf()).isEqualTo(DerivationFixtures.NOTICE_DAY);
            });
    assertThat(result.creditsSpent()).isEqualTo(3 * model.charge);
    assertThat(result.notDerived()).isEmpty();
    assertThat(store.rows).hasSize(4);
  }

  @Test
  @DisplayName("law.derive.max-credits 0: derivation is off, nothing is read or asked")
  void a_cap_of_zero_turns_derivation_off() {
    settings.deriveMaxCredits = 0;

    RuleDerivationResult result = service.derive(visitNotice());

    assertThat(result.outcome()).isEqualTo(Outcome.DERIVATION_OFF);
    assertThat(grounded.calls).isZero();
    assertThat(model.prompts).isEmpty();
    assertThat(store.writes).isZero();
  }

  @Test
  @DisplayName("the run stops at law.derive.max-credits: no call that would pass it is made")
  void the_run_stops_at_the_credit_cap() {
    // Each call costs 400 and may cost up to 500: 0 + 500 and 400 + 500 fit in 1000, 800 + 500 not.
    model.ceiling = 500;
    model.charge = 400;
    settings.deriveMaxCredits = 1_000;

    RuleDerivationResult result = service.derive(visitNotice());

    assertThat(result.outcome()).isEqualTo(Outcome.BUDGET_EXHAUSTED);
    assertThat(model.prompts).extracting(p -> p.article().ref()).containsExactly("1931", "1932");
    assertThat(result.creditsSpent()).isEqualTo(800).isLessThanOrEqualTo(1_000);
    assertThat(result.notDerived()).extracting(CitedArticle::ref).containsExactly("1933");
    // What was paid for is still vetted and recorded.
    assertThat(result.proposals())
        .extracting(RuleProposal::ruleId)
        .containsExactly("QC-VISIT-001", "QC-VISIT-002", "QC-VISIT-003");
    assertThat(result.proposals()).allMatch(p -> p.status() == ProposalStatus.PROPOSED);
  }

  @Test
  @DisplayName("a cap below one call's ceiling makes no call at all")
  void a_cap_below_one_call_makes_no_call() {
    model.ceiling = 500;
    settings.deriveMaxCredits = 499;

    RuleDerivationResult result = service.derive(visitNotice());

    assertThat(result.outcome()).isEqualTo(Outcome.BUDGET_EXHAUSTED);
    assertThat(model.prompts).isEmpty();
    assertThat(result.creditsSpent()).isZero();
    assertThat(result.proposals()).isEmpty();
    assertThat(result.notDerived())
        .extracting(CitedArticle::ref)
        .containsExactly("1931", "1932", "1933");
  }

  @Test
  @DisplayName("only verified claims are derived from: an article with none gets no call")
  void an_article_with_no_verified_claim_is_not_derived_from() {
    grounded.unverified = Set.of("1933");

    RuleDerivationResult result = service.derive(visitNotice());

    assertThat(model.prompts).extracting(p -> p.article().ref()).containsExactly("1931", "1932");
    assertThat(result.proposals()).extracting(RuleProposal::ruleId).doesNotContain("QC-VISIT-004");
  }

  @Test
  @DisplayName("a retrieval with no verified claim derives nothing and asks no model")
  void no_verified_claim_no_call() {
    grounded.unverified = Set.of("1931", "1932", "1933");

    RuleDerivationResult result = service.derive(visitNotice());

    assertThat(result.outcome()).isEqualTo(Outcome.NO_VERIFIED_CLAIMS);
    assertThat(model.prompts).isEmpty();
    assertThat(store.writes).isZero();
  }

  @Test
  @DisplayName("a retrieval that is off derives nothing")
  void a_retrieval_that_is_off_derives_nothing() {
    settings.research = false;

    RuleDerivationResult result = service.derive(visitNotice());

    assertThat(result.outcome()).isEqualTo(Outcome.NOT_RETRIEVED);
    assertThat(result.retrieval()).isEqualTo(LawRetrievalResult.Outcome.DISABLED);
    assertThat(model.prompts).isEmpty();
  }

  @Test
  @DisplayName("a recorded answer citing an article the corpus does not hold is REJECTED_AUTO")
  void an_answer_citing_an_article_outside_the_corpus_is_rejected() {
    model.answers.put("1931", DerivationFixtures.outsideCorpusAnswer());

    RuleDerivationResult result = service.derive(visitNotice());

    RuleProposal outside =
        result.proposals().stream()
            .filter(p -> "QC-VISIT-090".equals(p.ruleId()))
            .findFirst()
            .orElseThrow();
    assertThat(outside.status()).isEqualTo(ProposalStatus.REJECTED_AUTO);
    assertThat(outside.vetReport().reasons()).containsExactly(VetReason.LAW_VERSION_UNKNOWN);
    assertThat(result.proposals())
        .filteredOn(p -> !"QC-VISIT-090".equals(p.ruleId()))
        .allMatch(p -> p.status() == ProposalStatus.PROPOSED);
  }

  @Test
  @DisplayName("past law.derive.max-rules-per-kind no further call is made")
  void the_rule_limit_stops_the_calls() {
    settings.deriveMaxRules = 2;

    RuleDerivationResult result = service.derive(visitNotice());

    assertThat(model.prompts).extracting(p -> p.article().ref()).containsExactly("1931");
    assertThat(model.prompts.getFirst().rulesLeft()).isEqualTo(2);
    assertThat(result.proposals()).hasSize(2).allMatch(p -> p.status() == ProposalStatus.PROPOSED);
  }

  @Test
  @DisplayName("a second derivation of the same rules is revision 2 and supersedes revision 1")
  void a_second_derivation_supersedes_the_unreviewed_first() {
    service.derive(visitNotice());
    RuleDerivationResult again = service.derive(visitNotice());

    assertThat(again.proposals()).allMatch(p -> p.proposalRevision() == 2);
    assertThat(store.list(ProposalStatus.SUPERSEDED, KIND)).hasSize(4);
    assertThat(store.list(ProposalStatus.PROPOSED, KIND)).hasSize(4);
  }

  @Test
  @DisplayName("an article whose text moved since the retrieval gets no call and is listed")
  void an_article_whose_text_moved_is_not_derived_from_and_is_listed() {
    // The corpus the lookup reads moved after the retrieval: 1933 has a new text in force on the
    // day asked, so the claims verified on the old one cannot ground a proposal.
    InMemoryLawCorpus moved = new InMemoryLawCorpus();
    UUID importId = UUID.randomUUID();
    for (LawArticle article : DerivationFixtures.visitArticles()) {
      moved.add(importId, "donnees-quebec", article, NOW);
      if ("1933".equals(article.ref())) {
        moved.replace(
            importId,
            article,
            new LawArticle(
                article.jurisdiction(),
                article.instrument(),
                article.ref(),
                article.language(),
                article.inForceFrom().plusDays(1),
                article.text() + " Texte modifié.",
                article.officialUrl(),
                article.sourceUrl()),
            NOW);
      }
    }
    RuleDerivationService raced =
        new RuleDerivationService(
            settings,
            retrievals,
            new LawStatuteLookupService(moved, settings, clock),
            model,
            new RuleProposalService(store, clock));

    RuleDerivationResult result = raced.derive(visitNotice());

    assertThat(model.prompts).extracting(p -> p.article().ref()).containsExactly("1931", "1932");
    assertThat(result.notDerived()).extracting(CitedArticle::ref).containsExactly("1933");
  }

  @Test
  @DisplayName("a model whose call has no cost is refused: the cap could not hold it")
  void a_model_with_no_ceiling_is_refused() {
    model.ceiling = 0;

    assertThatThrownBy(() -> service.derive(visitNotice()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(grounded.calls).isZero();
  }

  /** The derivation model, answering from the recordings of the visit notice. */
  private static final class RecordedDerivationModel implements RuleDerivationModel {
    final Map<String, List<ProposedRule>> answers =
        new HashMap<>(DerivationFixtures.visitAnswers());
    final List<Prompt> prompts = new ArrayList<>();
    long ceiling = 100;
    long charge = 60;

    @Override
    public long callCeiling() {
      return ceiling;
    }

    @Override
    public Answer propose(Prompt prompt) {
      prompts.add(prompt);
      return new Answer(answers.getOrDefault(prompt.article().ref(), List.of()), charge);
    }
  }

  /** The grounded model of stage 2: one claim per article, quoting its first sentence. */
  private static final class GroundedModel implements GroundedClaimsModel {
    int calls;
    Set<String> unverified = Set.of();

    @Override
    public GroundedAnswer ask(String question, String language, List<Passage> given) {
      calls++;
      List<Claim> claims = new ArrayList<>();
      for (Passage p : given) {
        String ref = p.title().substring(p.title().lastIndexOf(' ') + 1);
        // A quote the article does not contain stays TO_VALIDATE: the verifier reads the text.
        String quote =
            unverified.contains(ref)
                ? "Le locateur entre quand il le veut."
                : p.text().substring(ref.length() + 1).split("(?<=[.;]) ")[0];
        claims.add(
            new Claim(
                "art. " + ref + " : " + quote,
                ClaimStatus.TO_VALIDATE,
                null,
                List.of(new Citation(p.url(), quote, p.contentHash(), "2026-10-24T12:00:00Z"))));
      }
      return new GroundedAnswer("", claims, List.of());
    }
  }

  /** A run budget that holds every call: the cap under test is the derivation's own. */
  private static final class FreeMeter implements ResearchMeter {
    @Override
    public Hold hold(String runId, String actorId, String providerKey) {
      return new FreeHold();
    }
  }

  private static final class FreeHold implements ResearchMeter.Hold {
    @Override
    public void settle() {
      // nothing is charged in this test
    }

    @Override
    public void release(String reason) {
      // nothing was held
    }
  }

  /** Research settings as seeded. */
  private static final class SeededConfig implements RuntimeConfigProvider {
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
      return defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
      return defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
      return defaultValue;
    }
  }
}
