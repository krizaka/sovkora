package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import com.krizaka.sovkora.tools.application.port.law.LawFileReader;
import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import java.io.ByteArrayInputStream;
import java.util.List;

/** Reads an uploaded statute with {@link LawStatuteXmlParser}: the importers' own parser. */
public final class LawXmlFileReader implements LawFileReader {

  private final LawStatuteXmlParser parser;

  /**
   * Creates the reader.
   *
   * @param parser the parser the importers use
   */
  public LawXmlFileReader(LawStatuteXmlParser parser) {
    this.parser = parser;
  }

  @Override
  public List<LawArticle> read(
      String jurisdiction, String instrument, String language, byte[] xml, String sourceUrl) {
    ByteArrayInputStream in = new ByteArrayInputStream(xml);
    return switch (jurisdiction) {
      case "QC" -> parser.parseQuebec(in, instrument, language, sourceUrl);
      case "CA" -> parser.parseJustice(in, instrument, language, sourceUrl);
      default -> throw new IllegalArgumentException("no law format is known for " + jurisdiction);
    };
  }
}
