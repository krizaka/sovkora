package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.LawCorpusCatalogue;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleHistory;
import com.krizaka.sovkora.tools.domain.model.law.LawImportSummary;
import com.krizaka.sovkora.tools.domain.model.law.LawInstrumentSummary;
import com.krizaka.sovkora.tools.domain.model.law.LawVersionSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The Corpus tab of the review queue (ADR-089 §8): the instruments, the imports with their hashes
 * and counts, and an article by point in time. Metadata only: an article's text leaves the corpus
 * only through the checked, metered read ({@code LawArticleReadService}).
 */
public class LawCorpusCatalogueService {

  /** The most imports one page lists. */
  static final int MAX_IMPORTS = 200;

  private final LawCorpusCatalogue catalogue;

  /**
   * Wires the service.
   *
   * @param catalogue the corpus, read for the queue
   */
  public LawCorpusCatalogueService(LawCorpusCatalogue catalogue) {
    this.catalogue = Objects.requireNonNull(catalogue, "LawCorpusCatalogue");
  }

  /**
   * The instruments.
   *
   * @return by jurisdiction and code
   */
  public List<LawInstrumentSummary> instruments() {
    return catalogue.instruments();
  }

  /**
   * The imports.
   *
   * @param limit the most rows, between 1 and {@value #MAX_IMPORTS}
   * @return the newest first
   * @throws IllegalArgumentException when the limit is out of bounds
   */
  public List<LawImportSummary> imports(int limit) {
    if (limit < 1 || limit > MAX_IMPORTS) {
      throw new IllegalArgumentException("limit is between 1 and " + MAX_IMPORTS);
    }
    return catalogue.imports(limit);
  }

  /**
   * An article by point in time.
   *
   * @param jurisdiction the jurisdiction
   * @param instrument the instrument code
   * @param ref the article reference
   * @param language {@code fr} or {@code en}
   * @param asOf the day
   * @return its versions and the one in force on that day, if any
   * @throws IllegalArgumentException when a part is missing
   */
  public LawArticleHistory article(
      String jurisdiction, String instrument, String ref, String language, LocalDate asOf) {
    String jur = required(jurisdiction, "jurisdiction").toUpperCase(Locale.ROOT);
    String code = required(instrument, "instrument");
    String article = required(ref, "ref");
    String lang = required(language, "language");
    if (!"fr".equals(lang) && !"en".equals(lang)) {
      throw new IllegalArgumentException("language is fr or en");
    }
    if (asOf == null) {
      throw new IllegalArgumentException("asOf is required");
    }
    List<LawVersionSummary> versions = catalogue.versions(jur, code, article, lang);
    LawVersionSummary inForce =
        versions.stream().filter(v -> v.inForceOn(asOf)).findFirst().orElse(null);
    return new LawArticleHistory(jur, code, article, lang, asOf, inForce, versions);
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value.trim();
  }
}
