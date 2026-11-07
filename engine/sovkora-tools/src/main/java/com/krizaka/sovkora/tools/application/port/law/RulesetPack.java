package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.tools.domain.model.law.GoldenCase;
import com.krizaka.sovkora.tools.domain.model.law.PackKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What the engine reads of the rulesets pack (sovkora-packs {@code document-validation}, on its
 * base branch) before it derives or promotes: a kind, its golden cases, the revision of a ruleset.
 */
public interface RulesetPack {

  /**
   * A kind.
   *
   * @param kindId the kind
   * @return the kind, or empty when the pack has none of that id
   */
  Optional<PackKind> kind(String kindId);

  /**
   * The golden cases of a kind ({@code worker/fixtures/<kindId>/*.txt}).
   *
   * @param kindId the kind, which is also its ruleset id
   * @return the cases; empty when it has none
   */
  List<GoldenCase> goldenCases(String kindId);

  /**
   * The revision of the ruleset of a kind at a version, when it exists.
   *
   * @param kindId the kind
   * @param effectiveFrom the version
   * @return its {@code revision}, or empty when that version is new
   */
  OptionalInt rulesetRevision(String kindId, LocalDate effectiveFrom);
}
