package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.LawSource;
import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawSnapshot;
import com.krizaka.sovkora.tools.domain.model.law.LawTarget;
import com.krizaka.sovkora.tools.domain.model.law.Sha256;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.LawStatuteXmlParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A recorded document, parsed by the real parser; the test chooses which one a run reads. */
final class FixtureLawSource implements LawSource {

  static final String DATASET = "https://donnees.test/lois.zip";

  private final LawStatuteXmlParser parser = new LawStatuteXmlParser();
  private String resource;
  private boolean failNext;
  private String etag;
  Optional<String> lastKnownEtag = Optional.empty();

  void serve(String resource) {
    this.resource = resource;
  }

  void failNext() {
    failNext = true;
  }

  /** The publisher answers 304 to a validator equal to this one. */
  void validator(String etag) {
    this.etag = etag;
  }

  @Override
  public String sourceKey() {
    return "donnees-quebec";
  }

  @Override
  public boolean serves(String jurisdiction) {
    return "QC".equals(jurisdiction);
  }

  @Override
  public String datasetUrl(String jurisdiction) {
    return DATASET;
  }

  @Override
  public LawSnapshot fetch(
      String jurisdiction, Collection<LawTarget> targets, Optional<String> knownEtag) {
    lastKnownEtag = knownEtag;
    if (failNext) {
      failNext = false;
      throw new IllegalStateException("Données Québec answered 503");
    }
    if (etag != null && knownEtag.filter(etag::equals).isPresent()) {
      return LawSnapshot.notModified(DATASET, etag);
    }
    byte[] xml = bytes(resource);
    List<LawArticle> articles =
        parser.parseQuebec(new ByteArrayInputStream(xml), "CCQ-1991", "fr", DATASET);
    return new LawSnapshot(articles, Set.of("fr"), DATASET, etag, Sha256.hex(xml), null, false);
  }

  static byte[] bytes(String resource) {
    try (InputStream in = FixtureLawSource.class.getResourceAsStream(resource)) {
      return Objects.requireNonNull(in, resource).readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
