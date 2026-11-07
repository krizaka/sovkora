package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.core.domain.model.research.VerifyMode;
import com.krizaka.sovkora.tools.domain.model.law.LawTarget;
import java.util.List;

/**
 * The {@code law.*} settings in force, read from {@code orazaka_runtime_config} at call time: the
 * administrator changes them without a restart. Nothing is hard-coded by the callers.
 */
public interface LawSettings {

  /**
   * The master switch of the corpus ({@code law.corpus.enabled}).
   *
   * @return true when imports and lookups run
   */
  boolean corpusEnabled();

  /**
   * The instruments the corpus stores ({@code law.import.instruments}); nothing else is stored.
   *
   * @return one whole-instrument target per entry, in the setting's order
   */
  List<LawTarget> importInstruments();

  /**
   * The longest article text stored ({@code law.import.max-article-chars}).
   *
   * @return the limit in characters
   */
  int maxArticleChars();

  /**
   * The master switch of grounded research ({@code research.enabled}): a retrieval is grounded
   * research, so it needs this one as well as the corpus.
   *
   * @return true when grounded research may run
   */
  boolean researchEnabled();

  /**
   * The most articles one retrieval keeps for a kind ({@code law.retrieval.max-articles}).
   *
   * @return the limit
   */
  int retrievalMaxArticles();

  /**
   * How long a cached retrieval is kept even if no cited article changed, since new articles may
   * apply ({@code law.retrieval.max-age-days}).
   *
   * @return the age in days
   */
  int retrievalMaxAgeDays();

  /**
   * What the verifier does with an unverified claim ({@code research.verify.mode}).
   *
   * @return {@code MARK} (the default) or {@code STRICT}
   */
  VerifyMode verifyMode();

  /**
   * The credit cap of one derivation run ({@code law.derive.max-credits}); 0 turns derivation off.
   *
   * @return the cap in credits
   */
  int deriveMaxCredits();

  /**
   * The most rules one derivation may propose for a kind ({@code law.derive.max-rules-per-kind}).
   *
   * @return the limit
   */
  int deriveMaxRulesPerKind();

  /**
   * The golden documents a kind needs before promotion ({@code law.review.min-golden-cases}): one
   * clean, one with errors and the blank form among them.
   *
   * @return the minimum
   */
  int reviewMinGoldenCases();
}
