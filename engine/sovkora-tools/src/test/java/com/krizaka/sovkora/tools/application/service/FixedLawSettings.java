package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.core.domain.model.research.VerifyMode;
import com.krizaka.sovkora.tools.application.port.law.LawSettings;
import com.krizaka.sovkora.tools.domain.model.law.LawTarget;
import java.util.List;

/** The law settings as a test sets them. */
final class FixedLawSettings implements LawSettings {

  boolean enabled = true;
  int maxArticleChars = 20_000;
  boolean research = true;
  int maxArticles = 40;
  int maxAgeDays = 90;
  VerifyMode mode = VerifyMode.MARK;
  int deriveMaxCredits = 2000;
  int deriveMaxRules = 30;
  int minGoldenCases = 3;
  List<LawTarget> instruments =
      List.of(LawTarget.wholeInstrument("QC", "CCQ-1991"), LawTarget.wholeInstrument("CA", "E-15"));

  @Override
  public boolean corpusEnabled() {
    return enabled;
  }

  @Override
  public List<LawTarget> importInstruments() {
    return instruments;
  }

  @Override
  public int maxArticleChars() {
    return maxArticleChars;
  }

  @Override
  public boolean researchEnabled() {
    return research;
  }

  @Override
  public int retrievalMaxArticles() {
    return maxArticles;
  }

  @Override
  public int retrievalMaxAgeDays() {
    return maxAgeDays;
  }

  @Override
  public VerifyMode verifyMode() {
    return mode;
  }

  @Override
  public int deriveMaxCredits() {
    return deriveMaxCredits;
  }

  @Override
  public int deriveMaxRulesPerKind() {
    return deriveMaxRules;
  }

  @Override
  public int reviewMinGoldenCases() {
    return minGoldenCases;
  }
}
