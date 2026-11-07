package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import java.util.List;

/** Reads an uploaded consolidated statute with the parser the importers use. */
public interface LawFileReader {

  /**
   * Parses one file.
   *
   * @param jurisdiction {@code CA} or {@code QC}; it chooses the publisher's format
   * @param instrument the instrument code the file holds
   * @param language {@code fr} or {@code en}
   * @param xml the file
   * @param sourceUrl the address the file came from
   * @return every article in force in the file, with its in-force date
   * @throws IllegalArgumentException when the file is not a statute of that format
   */
  List<LawArticle> read(
      String jurisdiction, String instrument, String language, byte[] xml, String sourceUrl);
}
