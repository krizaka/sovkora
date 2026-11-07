package com.krizaka.sovkora.tools.domain.model.law;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What a promotion proposes to sovkora-packs (ADR-089 §5 step 4): one dated ruleset revision of a
 * kind, the article versions it pins, and where it comes from. The merge of the pull request is the
 * promotion; nothing here is executable before it.
 *
 * @param kindId the kind, which is also the ruleset id
 * @param jurisdiction the jurisdiction
 * @param effectiveFrom the law's in-force day, which is the ruleset's {@code version}
 * @param revision 1 for a new version, one more for our own correction of it
 * @param revisionNote what this revision changes
 * @param rules the rules, in the order of the request
 * @param articles the article versions the rules pin, each once
 * @param provenance the derivation, the proposals, the review and the imports
 */
public record RulesetPromotion(
    String kindId,
    String jurisdiction,
    LocalDate effectiveFrom,
    int revision,
    String revisionNote,
    List<PromotedRule> rules,
    List<PinnedArticle> articles,
    Provenance provenance) {

  /** The review status a promoted ruleset is written with (ADR-089 §5 step 3). */
  public static final String REVIEW_STATUS = "REVIEWED_INTERNAL";

  /** The mark that stays shown until a lawyer signs the ruleset off. */
  public static final String LAWYER_MARK = "TO_REVIEW_BY_LAWYER";

  /** Copies the lists. */
  public RulesetPromotion {
    Objects.requireNonNull(kindId, "kindId");
    Objects.requireNonNull(jurisdiction, "jurisdiction");
    Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    Objects.requireNonNull(provenance, "provenance");
    if (revision < 1) {
      throw new IllegalArgumentException("a revision starts at 1");
    }
    rules = List.copyOf(rules);
    articles = List.copyOf(articles);
  }

  /**
   * The ruleset's path in the pack.
   *
   * @return {@code document-validation/rulesets/<kindId>/<effectiveFrom>.json}
   */
  public String rulesetPath() {
    return "document-validation/rulesets/" + kindId + "/" + effectiveFrom + ".json";
  }

  /**
   * Where the ruleset comes from.
   *
   * @param derivationRunIds the derivations that proposed the rules
   * @param proposalIds the {@code rule_proposal} rows promoted
   * @param reviewedBy who approved them
   * @param reviewedAt the last approval
   * @param lawImportIds the {@code law_import} rows that brought the pinned texts in
   */
  public record Provenance(
      List<UUID> derivationRunIds,
      List<UUID> proposalIds,
      List<String> reviewedBy,
      Instant reviewedAt,
      List<UUID> lawImportIds) {

    /** Copies the lists. */
    public Provenance {
      derivationRunIds = List.copyOf(derivationRunIds);
      proposalIds = List.copyOf(proposalIds);
      reviewedBy = List.copyOf(reviewedBy);
      lawImportIds = List.copyOf(lawImportIds);
    }
  }
}
