package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.tools.domain.model.law.LawImportSummary;
import com.krizaka.sovkora.tools.domain.model.law.LawInstrumentSummary;
import com.krizaka.sovkora.tools.domain.model.law.LawVersionSummary;
import java.util.List;

/**
 * What the review queue shows of the corpus: its instruments, its imports and the versions of an
 * article. Metadata only, never an article's text.
 */
public interface LawCorpusCatalogue {

  /**
   * The instruments, with their counts.
   *
   * @return by jurisdiction and code
   */
  List<LawInstrumentSummary> instruments();

  /**
   * The imports.
   *
   * @param limit the most rows
   * @return the newest first
   */
  List<LawImportSummary> imports(int limit);

  /**
   * The versions of an article.
   *
   * @param jurisdiction the jurisdiction
   * @param instrument the instrument code
   * @param ref the article reference
   * @param language {@code fr} or {@code en}
   * @return the newest first; empty when unknown
   */
  List<LawVersionSummary> versions(
      String jurisdiction, String instrument, String ref, String language);
}
