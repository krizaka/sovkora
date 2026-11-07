package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import com.krizaka.sovkora.tools.application.port.law.LawSource;
import com.krizaka.sovkora.tools.domain.model.law.LawSnapshot;
import com.krizaka.sovkora.tools.domain.model.law.LawTarget;
import com.krizaka.sovkora.tools.domain.model.law.Sha256;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.client.RestClient;

/**
 * Federal law from Justice Canada's consolidated XML ({@code /eng/XML/<code>.xml} and {@code
 * /fra/XML/<code>.xml}), open data under the Open Government Licence - Canada. One file per act and
 * language, so there is no dataset validator: every run reads them.
 */
public final class JusticeLawsXmlSource implements LawSource {

  /** The {@code research_source} the Justice Canada texts are recorded under. */
  static final String SOURCE_KEY = "justice-laws";

  private static final String BASE = "https://laws-lois.justice.gc.ca/";

  private final RestClient client;
  private final LawStatuteXmlParser parser;

  /**
   * Creates the source.
   *
   * @param client the HTTP client, with an honest user agent
   * @param parser the XML parser
   */
  public JusticeLawsXmlSource(RestClient client, LawStatuteXmlParser parser) {
    this.client = client;
    this.parser = parser;
  }

  @Override
  public String sourceKey() {
    return SOURCE_KEY;
  }

  @Override
  public boolean serves(String jurisdiction) {
    return "CA".equals(jurisdiction);
  }

  @Override
  public String datasetUrl(String jurisdiction) {
    return BASE;
  }

  @Override
  public LawSnapshot fetch(
      String jurisdiction, Collection<LawTarget> targets, Optional<String> knownEtag) {
    List<LawSnapshot> files = new ArrayList<>();
    for (LawTarget target : targets) {
      files.add(read("eng", "en", target.instrument()));
      files.add(read("fra", "fr", target.instrument()));
    }
    return LawSnapshot.merge(BASE, files);
  }

  private LawSnapshot read(String path, String language, String instrument) {
    String url = BASE + path + "/XML/" + instrument + ".xml";
    byte[] xml = client.get().uri(url).retrieve().body(byte[].class);
    if (xml == null || xml.length == 0) {
      throw new IllegalStateException("the federal XML of " + instrument + " answered empty");
    }
    return new LawSnapshot(
        parser.parseJustice(new ByteArrayInputStream(xml), instrument, language, url),
        Set.of(language),
        url,
        null,
        Sha256.hex(xml),
        parser.federalConsolidatedTo(xml).orElse(null),
        false);
  }
}
