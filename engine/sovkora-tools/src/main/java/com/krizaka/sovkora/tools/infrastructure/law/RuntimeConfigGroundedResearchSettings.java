package com.krizaka.sovkora.tools.infrastructure.law;

import com.krizaka.sovkora.core.domain.model.research.VerifyMode;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.application.port.law.GroundedResearchSettings;
import java.util.Objects;

/**
 * {@link GroundedResearchSettings} over the runtime settings ({@code research.*}, {@code law.*}). A
 * missing row reads as off, or as the seeded bound.
 */
public final class RuntimeConfigGroundedResearchSettings implements GroundedResearchSettings {

  static final String KEY_RESEARCH_ENABLED = "research.enabled";
  static final String KEY_CORPUS_ENABLED = "law.corpus.enabled";
  static final String KEY_LECTURE_ENABLED = "law.lecture.enabled";
  static final String KEY_LECTURE_MAX_CREDITS = "law.lecture.max-credits";
  static final String KEY_VERIFY_MODE = "research.verify.mode";
  static final String KEY_MAX_ARTICLES = "law.retrieval.max-articles";
  static final String KEY_QUOTE_MAX_CHARS = "research.quote.max-chars";

  /** The seeded defaults, when a row is missing. */
  private static final int SEEDED_MAX_ARTICLES = 40;

  private static final int SEEDED_QUOTE_MAX_CHARS = 300;

  private final RuntimeConfigProvider config;

  /**
   * Creates the reader.
   *
   * @param config the runtime settings
   */
  public RuntimeConfigGroundedResearchSettings(RuntimeConfigProvider config) {
    this.config = Objects.requireNonNull(config, "RuntimeConfigProvider cannot be null");
  }

  @Override
  public boolean researchEnabled() {
    return config.getBoolean(KEY_RESEARCH_ENABLED, false);
  }

  @Override
  public boolean corpusEnabled() {
    return config.getBoolean(KEY_CORPUS_ENABLED, false);
  }

  @Override
  public boolean lectureEnabled() {
    return config.getBoolean(KEY_LECTURE_ENABLED, false);
  }

  /** A missing row is a cap of 0: a lecture spends nothing it was not allowed to. */
  @Override
  public int lectureMaxCredits() {
    return config.getInt(KEY_LECTURE_MAX_CREDITS, 0);
  }

  @Override
  public VerifyMode verifyMode() {
    return "STRICT".equalsIgnoreCase(config.getString(KEY_VERIFY_MODE, "MARK"))
        ? VerifyMode.STRICT
        : VerifyMode.MARK;
  }

  @Override
  public int maxArticles() {
    return config.getInt(KEY_MAX_ARTICLES, SEEDED_MAX_ARTICLES);
  }

  @Override
  public int quoteMaxChars() {
    return config.getInt(KEY_QUOTE_MAX_CHARS, SEEDED_QUOTE_MAX_CHARS);
  }
}
