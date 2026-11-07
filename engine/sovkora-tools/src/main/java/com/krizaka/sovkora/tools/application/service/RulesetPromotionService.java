package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.LawSettings;
import com.krizaka.sovkora.tools.application.port.law.RulesetPack;
import com.krizaka.sovkora.tools.application.port.law.RulesetPromotionPublisher;
import com.krizaka.sovkora.tools.domain.model.law.*;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal.Code;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stage 5 of ADR-089: approved proposals become a dated ruleset revision, proposed to sovkora-packs
 * as a pull request. It refuses, before anything is opened:
 *
 * <ul>
 *   <li>a proposal that is not {@code APPROVED}, or already in a pull request, or of another kind,
 *       or a second proposal of the same rule;
 *   <li>a kind below {@code law.review.min-golden-cases} (one clean, one with known errors and the
 *       blank form among them);
 *   <li>a rule whose pinned hash is not the version in force on {@code effectiveFrom}, or whose
 *       quote is longer than a ruleset holds.
 * </ul>
 *
 * The status checks run again while the rows are locked, so a concurrent review or promotion cannot
 * slip between the check and the pull request. The pull request is the review of the pack; its
 * merge is the promotion, and the proposals carry its address meanwhile.
 */
public class RulesetPromotionService {

  private final RulesetPack pack;
  private final RulesetPromotionPublisher publisher;
  private final RuleProposalService proposals;
  private final LawStatuteLookupService lookups;
  private final LawSettings settings;

  /**
   * Wires the promotion.
   *
   * @param proposals the rows promoted, read and locked through their one door
   * @param lookups the corpus, which pins every quoted article on the ruleset's day
   * @param settings {@code law.review.min-golden-cases}, read at each promotion
   * @param pack the rulesets pack, read: golden cases and ruleset revisions
   * @param publisher opens the pull request
   */
  public RulesetPromotionService(
      RuleProposalService proposals,
      LawStatuteLookupService lookups,
      LawSettings settings,
      RulesetPack pack,
      RulesetPromotionPublisher publisher) {
    this.proposals = Objects.requireNonNull(proposals, "RuleProposalService");
    this.lookups = Objects.requireNonNull(lookups, "LawStatuteLookupService");
    this.settings = Objects.requireNonNull(settings, "LawSettings");
    this.pack = Objects.requireNonNull(pack, "RulesetPack");
    this.publisher = Objects.requireNonNull(publisher, "RulesetPromotionPublisher");
  }

  /**
   * The golden cases of a kind against the minimum.
   *
   * @param kindId the kind
   * @return the cases and whether they meet {@code law.review.min-golden-cases}
   * @throws LawReviewRefusal {@code LAW_PROMOTION_OFF} when the pack is not configured
   */
  public GoldenCases goldenCases(String kindId) {
    String kind = requireKind(kindId);
    return new GoldenCases(kind, settings.reviewMinGoldenCases(), pack.goldenCases(kind));
  }

  /**
   * Opens the promotion of approved proposals.
   *
   * @param kindId the kind, which is the ruleset id
   * @param ids the proposals, in the order of the ruleset
   * @param effectiveFrom the law's in-force day, the ruleset's {@code version}
   * @param revisionNote what this revision changes, optional
   * @return the pull request
   * @throws IllegalArgumentException when no proposal or no day is given, or an id repeats
   * @throws LawReviewRefusal for every refusal listed on the class
   */
  public PromotionResult promote(
      String kindId, List<UUID> ids, LocalDate effectiveFrom, String revisionNote) {
    String kind = requireKind(kindId);
    if (ids == null || ids.isEmpty() || ids.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("a promotion names its proposals");
    }
    if (new HashSet<>(ids).size() != ids.size()) {
      throw new IllegalArgumentException("a proposal is named twice");
    }
    if (effectiveFrom == null) {
      throw new IllegalArgumentException("effectiveFrom is required");
    }
    List<RuleProposal> rows = new ArrayList<>();
    for (UUID id : ids) {
      rows.add(
          proposals
              .find(id)
              .orElseThrow(
                  () -> new LawReviewRefusal(Code.PROPOSAL_NOT_FOUND, "no proposal " + id)));
    }
    checkPromotable(kind, rows);
    List<PromotedRule> rules = new ArrayList<>();
    Map<String, PinnedArticle> articles = new LinkedHashMap<>();
    for (RuleProposal row : rows) {
      rules.add(pin(row, effectiveFrom, articles));
    }
    GoldenCases golden = goldenCases(kind);
    if (!golden.met()) {
      throw new LawReviewRefusal(
          Code.GOLDEN_MINIMUM_NOT_MET,
          kind
              + " has "
              + golden.cases().size()
              + " golden cases ("
              + golden.compliant()
              + " compliant, "
              + golden.defective()
              + " defective, "
              + golden.blank()
              + " blank); promotion needs "
              + golden.required()
              + " with one of each");
    }
    int revision = pack.rulesetRevision(kind, effectiveFrom).orElse(0) + 1;
    String note = revisionNote == null || revisionNote.isBlank() ? null : revisionNote.trim();
    if (revision > 1 && note == null) {
      throw new IllegalArgumentException(
          kind + " " + effectiveFrom + " exists: revision " + revision + " says what it corrects");
    }
    AtomicReference<String> branch = new AtomicReference<>();
    String prUrl =
        proposals.promote(
            ids,
            locked -> {
              if (locked.size() != ids.size()) {
                throw new LawReviewRefusal(
                    Code.PROPOSAL_NOT_FOUND, "a proposal disappeared before the promotion");
              }
              checkPromotable(kind, locked);
              RulesetPromotion promotion =
                  new RulesetPromotion(
                      kind,
                      locked.getFirst().jurisdiction(),
                      effectiveFrom,
                      revision,
                      note,
                      rules,
                      List.copyOf(articles.values()),
                      provenance(locked, articles.values()));
              RulesetPromotionPublisher.Opened opened = publisher.open(promotion);
              branch.set(opened.branch());
              return opened.prUrl();
            });
    return new PromotionResult(
        prUrl,
        branch.get(),
        "document-validation/rulesets/" + kind + "/" + effectiveFrom + ".json",
        revision,
        ids);
  }

  /** The rules of stage 5 on the rows, before the pull request and again under the lock. */
  private static void checkPromotable(String kindId, List<RuleProposal> rows) {
    Set<String> rules = new HashSet<>();
    String jurisdiction = rows.getFirst().jurisdiction();
    for (RuleProposal row : rows) {
      if (!row.kindId().equals(kindId) || !row.jurisdiction().equals(jurisdiction)) {
        throw new LawReviewRefusal(
            Code.KIND_MISMATCH, row.id() + " is a proposal of " + row.kindId() + ", not " + kindId);
      }
      if (row.status() != ProposalStatus.APPROVED) {
        throw new LawReviewRefusal(
            Code.PROPOSAL_NOT_APPROVED, row.id() + " is " + row.status() + ", not APPROVED");
      }
      if (row.packsPrUrl() != null) {
        throw new LawReviewRefusal(
            Code.ALREADY_IN_REVIEW, row.id() + " is already in " + row.packsPrUrl());
      }
      if (!rules.add(row.ruleId())) {
        throw new LawReviewRefusal(
            Code.DUPLICATE_RULE, row.ruleId() + " is promoted twice in one ruleset");
      }
    }
  }

  /** Pins each law block of a rule on the version in force on the ruleset's day. */
  private PromotedRule pin(
      RuleProposal row, LocalDate effectiveFrom, Map<String, PinnedArticle> articles) {
    List<PinnedLaw> pinned = new ArrayList<>();
    for (LawBlock block : row.rule().law()) {
      if (block.quote() != null && block.quote().length() > PinnedLaw.MAX_QUOTE) {
        throw new LawReviewRefusal(
            Code.QUOTE_TOO_LONG,
            row.ruleId()
                + " quotes "
                + block.quote().length()
                + " characters of art. "
                + block.ref()
                + "; a ruleset holds "
                + PinnedLaw.MAX_QUOTE);
      }
      StatuteVersion version = inForce(row, block, effectiveFrom);
      pinned.add(
          new PinnedLaw(
              block.instrument(),
              block.ref(),
              block.language(),
              version.inForceFrom(),
              version.contentHash(),
              version.officialUrl(),
              block.quote()));
      articles.putIfAbsent(
          version.contentHash(),
          new PinnedArticle(
              block.instrument(),
              block.ref(),
              block.language(),
              version.inForceFrom(),
              version.contentHash(),
              version.officialUrl(),
              version.text(),
              version.importId()));
    }
    return new PromotedRule(row.rule(), pinned);
  }

  private StatuteVersion inForce(RuleProposal row, LawBlock block, LocalDate effectiveFrom) {
    StatuteLookup found =
        lookups.lookupStatute(
            row.jurisdiction(), block.instrument(), block.ref(), block.language(), effectiveFrom);
    Optional<StatuteVersion> version =
        found.outcome() == StatuteLookup.Outcome.FOUND
            ? Optional.of(found.version())
            : Optional.empty();
    if (version.isEmpty() || !version.get().contentHash().equals(block.contentHash())) {
      throw new LawReviewRefusal(
          Code.LAW_VERSION_MISMATCH,
          row.ruleId()
              + " pins "
              + block.instrument()
              + " art. "
              + block.ref()
              + " at a version that is not the one in force on "
              + effectiveFrom
              + " ("
              + found.outcome()
              + "): derive the kind again");
    }
    return version.get();
  }

  private static RulesetPromotion.Provenance provenance(
      List<RuleProposal> rows, Collection<PinnedArticle> articles) {
    Set<UUID> runs = new LinkedHashSet<>();
    Set<String> reviewers = new LinkedHashSet<>();
    for (RuleProposal row : rows) {
      if (row.derivationRunId() != null) {
        runs.add(row.derivationRunId());
      }
      reviewers.add(row.reviewedBy());
    }
    Instant reviewedAt =
        rows.stream()
            .map(RuleProposal::reviewedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
    return new RulesetPromotion.Provenance(
        List.copyOf(runs),
        rows.stream().map(RuleProposal::id).toList(),
        List.copyOf(reviewers),
        reviewedAt,
        articles.stream()
            .map(PinnedArticle::importId)
            .filter(Objects::nonNull)
            .distinct()
            .toList());
  }

  private static String requireKind(String kindId) {
    if (kindId == null) {
      throw new IllegalArgumentException("a kind id is required");
    }
    return PackKind.requireKindId(kindId.trim());
  }
}
