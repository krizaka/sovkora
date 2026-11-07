package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.core.domain.model.research.Citation;
import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.core.domain.model.research.ClaimStatus;
import com.krizaka.sovkora.tools.application.port.law.LawSettings;
import com.krizaka.sovkora.tools.application.port.law.RuleDerivationModel;
import com.krizaka.sovkora.tools.domain.model.law.*;
import com.krizaka.sovkora.tools.domain.model.law.RuleDerivationResult.Outcome;
import java.util.*;

/**
 * Stage 3 of ADR-089: the engine proposes the checks of a kind from its law, and nothing it
 * proposes is a rule until it is promoted (stage 5).
 *
 * <ol>
 *   <li>{@code law.derive.max-credits} is 0: nothing runs.
 *   <li>The law of the kind is retrieved (stage 2, {@link LawRetrievalService}: its cache first).
 *       Only the {@code VERIFIED} claims are used; an article with none is not derived from.
 *   <li>One call of {@link RuleDerivationModel} per article, in the retrieval's order. Before each
 *       call, what was spent plus the call's ceiling must fit in {@code law.derive.max-credits};
 *       when it does not, the run stops ({@code BUDGET_EXHAUSTED}) and no further call is made.
 *       This is the run budget of ADR-088 applied to the one paid step of a derivation: when
 *       ADR-088 lands, the Studio run's {@code credit_cap} is this setting and its saga makes the
 *       same comparison before each step.
 *   <li>Every proposed rule goes through {@link RuleProposalVet} against the corpus in force on
 *       {@code asOf}, and is recorded through {@link RuleProposalService}: {@code PROPOSED} when it
 *       passed, {@code REJECTED_AUTO} with its reasons when it did not.
 * </ol>
 */
public class RuleDerivationService {

  private final LawSettings settings;
  private final LawRetrievalService retrievals;
  private final LawStatuteLookupService lookups;
  private final RuleDerivationModel model;
  private final RuleProposalService proposals;

  /**
   * Wires the service.
   *
   * @param settings the {@code law.*} settings
   * @param retrievals stage 2
   * @param lookups the corpus at a day
   * @param model the model port of the {@code propose} step
   * @param proposals the door to {@code rule_proposal}
   */
  public RuleDerivationService(
      LawSettings settings,
      LawRetrievalService retrievals,
      LawStatuteLookupService lookups,
      RuleDerivationModel model,
      RuleProposalService proposals) {
    this.settings = Objects.requireNonNull(settings, "LawSettings");
    this.retrievals = Objects.requireNonNull(retrievals, "LawRetrievalService");
    this.lookups = Objects.requireNonNull(lookups, "LawStatuteLookupService");
    this.model = Objects.requireNonNull(model, "RuleDerivationModel");
    this.proposals = Objects.requireNonNull(proposals, "RuleProposalService");
  }

  /**
   * Derives the proposed rules of one kind.
   *
   * @param kind the kind, its question, its targeted articles, the day, the run and who pays
   * @return what was written and spent
   */
  public RuleDerivationResult derive(LawRetrievalRequest kind) {
    Objects.requireNonNull(kind, "kind");
    long cap = settings.deriveMaxCredits();
    if (cap <= 0) {
      return RuleDerivationResult.nothing(Outcome.DERIVATION_OFF, null);
    }
    long ceiling = model.callCeiling();
    if (ceiling < 1) {
      throw new IllegalStateException("a derivation model call costs at least one credit");
    }
    LawRetrievalResult law = retrievals.retrieve(kind);
    if (law.outcome() != LawRetrievalResult.Outcome.CACHED
        && law.outcome() != LawRetrievalResult.Outcome.RETRIEVED) {
      return RuleDerivationResult.nothing(Outcome.NOT_RETRIEVED, law.outcome());
    }
    Map<CitedArticle, List<Claim>> grounded = verifiedClaimsByArticle(law);
    if (grounded.isEmpty()) {
      return RuleDerivationResult.nothing(Outcome.NO_VERIFIED_CLAIMS, law.outcome());
    }

    InForceArticles corpus = lookups.inForceOn(kind.jurisdiction(), kind.asOf());
    int maxRules = settings.deriveMaxRulesPerKind();
    List<ProposedRule> proposed = new ArrayList<>();
    List<CitedArticle> notDerived = new ArrayList<>();
    long spent = 0;
    boolean stopped = false;
    for (Map.Entry<CitedArticle, List<Claim>> entry : grounded.entrySet()) {
      CitedArticle article = entry.getKey();
      if (stopped || spent + ceiling > cap) {
        stopped = true;
        notDerived.add(article);
        continue;
      }
      if (proposed.size() >= maxRules) {
        break;
      }
      Optional<StatuteVersion> text =
          corpus.inForce(article.instrument(), article.ref(), article.language());
      if (text.isEmpty() || !text.get().contentHash().equals(article.contentHash())) {
        // The corpus moved since the retrieval: never propose from a text it no longer holds, and
        // say so, so the result accounts for every article that had verified claims.
        notDerived.add(article);
        continue;
      }
      RuleDerivationModel.Answer answer =
          model.propose(
              new RuleDerivationModel.Prompt(
                  kind.jurisdiction(),
                  kind.kindId(),
                  kind.language(),
                  kind.asOf(),
                  toModel(article, text.get()),
                  entry.getValue(),
                  maxRules - proposed.size()));
      spent += answer.credits();
      proposed.addAll(answer.rules());
    }

    List<VettedProposal> vetted = new RuleProposalVet(corpus, kind.asOf(), maxRules).vet(proposed);
    List<RuleProposal> written =
        proposals.record(kind.jurisdiction(), kind.kindId(), kind.runId(), vetted);
    return new RuleDerivationResult(
        stopped ? Outcome.BUDGET_EXHAUSTED : Outcome.DERIVED,
        law.outcome(),
        written,
        spent,
        notDerived);
  }

  /** The verified claims of each article, the articles in the retrieval's order. */
  private static Map<CitedArticle, List<Claim>> verifiedClaimsByArticle(LawRetrievalResult law) {
    Map<CitedArticle, List<Claim>> byArticle = new LinkedHashMap<>();
    for (CitedArticle article : law.articles()) {
      List<Claim> claims =
          law.claims().stream()
              .filter(c -> c.status() == ClaimStatus.VERIFIED)
              .filter(c -> cites(c, article))
              .toList();
      if (!claims.isEmpty()) {
        byArticle.put(article, claims);
      }
    }
    return byArticle;
  }

  private static boolean cites(Claim claim, CitedArticle article) {
    return claim.citations().stream()
        .map(Citation::contentHash)
        .anyMatch(article.contentHash()::equals);
  }

  private static RuleDerivationModel.Article toModel(CitedArticle article, StatuteVersion text) {
    return new RuleDerivationModel.Article(
        article.instrument(),
        article.ref(),
        article.language(),
        text.contentHash(),
        text.officialUrl(),
        text.text());
  }
}
