package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.RulesetPack;
import com.krizaka.sovkora.tools.domain.model.law.DerivationStarted;
import com.krizaka.sovkora.tools.domain.model.law.LawRetrievalRequest;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal.Code;
import com.krizaka.sovkora.tools.domain.model.law.PackKind;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * An administrator starts the derivation of a kind (ADR-089 §8, {@code POST
 * /api/v1/admin/law/derivations}): the kind's retrieval targets and question are read from the
 * pack, never typed by the caller, and the derivation runs under {@code law.derive.max-credits}.
 * Demand alone never starts one (§6).
 */
public class KindDerivationService {

  private final RulesetPack pack;
  private final RuleDerivationService derivations;

  /**
   * Wires the service.
   *
   * @param pack the rulesets pack
   * @param derivations stage 3
   */
  public KindDerivationService(RulesetPack pack, RuleDerivationService derivations) {
    this.pack = Objects.requireNonNull(pack, "RulesetPack");
    this.derivations = Objects.requireNonNull(derivations, "RuleDerivationService");
  }

  /**
   * Derives the proposed rules of a kind.
   *
   * @param jurisdiction the jurisdiction, which must be the kind's
   * @param kindId the kind
   * @param asOf the day the law must be in force
   * @param language the language of the question, {@code fr} or {@code en}
   * @param actorId the administrator, who the run is billed to
   * @return the run id and what it wrote
   * @throws LawReviewRefusal {@code KIND_NOT_FOUND}, or {@code LAW_DERIVATION_OFF} when the pack is
   *     not configured
   * @throws IllegalArgumentException when the jurisdiction is not the kind's, or the kind declares
   *     no retrieval in that language
   */
  public DerivationStarted start(
      String jurisdiction, String kindId, LocalDate asOf, String language, String actorId) {
    Objects.requireNonNull(asOf, "asOf");
    String lang = language == null || language.isBlank() ? "fr" : language.trim();
    PackKind kind = kind(kindId);
    if (jurisdiction == null
        || !kind.jurisdiction().equals(jurisdiction.trim().toUpperCase(Locale.ROOT))) {
      throw new IllegalArgumentException(kindId + " is a kind of " + kind.jurisdiction());
    }
    String question = kind.questions().get(lang);
    if (kind.targets().isEmpty() || question == null || question.isBlank()) {
      throw new IllegalArgumentException(kindId + " declares no retrieval in " + lang);
    }
    UUID runId = UUID.randomUUID();
    return new DerivationStarted(
        runId,
        derivations.derive(
            new LawRetrievalRequest(
                kind.jurisdiction(),
                kind.kindId(),
                question,
                lang,
                asOf,
                kind.targets(),
                runId,
                actorId)));
  }

  private PackKind kind(String kindId) {
    try {
      return pack.kind(PackKind.requireKindId(kindId))
          .orElseThrow(() -> new LawReviewRefusal(Code.KIND_NOT_FOUND, "no kind " + kindId));
    } catch (LawReviewRefusal off) {
      if (off.code() == Code.LAW_PROMOTION_OFF) {
        throw new LawReviewRefusal(
            Code.LAW_DERIVATION_OFF, "the rulesets pack is not configured: " + off.getMessage());
      }
      throw off;
    }
  }
}
