package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import com.krizaka.sovkora.tools.application.port.law.LawSource;
import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawSnapshot;
import com.krizaka.sovkora.tools.domain.model.law.LawTarget;
import com.krizaka.sovkora.tools.domain.model.law.Sha256;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/**
 * Québec law from the Données Québec open-data snapshot of the consolidated statutes and
 * regulations (XML, one zip per collection). Only the targeted instruments are read from it.
 */
public final class QuebecOpenDataSource implements LawSource {

  /** The {@code research_source} the Données Québec texts are recorded under. */
  static final String SOURCE_KEY = "donnees-quebec";

  private static final Pattern SNAPSHOT_DATE = Pattern.compile("/(\\d{8})_[^/]*$");
  private static final Pattern STATUTE_ENTRY =
      Pattern.compile("Statutes/([^/]+)/(FR|EN)/\\1_\\2\\.xml");

  private final RestClient client;
  private final String datasetUrl;
  private final LawStatuteXmlParser parser;

  /**
   * Creates the source.
   *
   * @param client the HTTP client, with an honest user agent
   * @param datasetUrl the URL of the statutes zip of the snapshot
   * @param parser the XML parser
   */
  public QuebecOpenDataSource(RestClient client, String datasetUrl, LawStatuteXmlParser parser) {
    this.client = client;
    this.datasetUrl = datasetUrl;
    this.parser = parser;
  }

  @Override
  public String sourceKey() {
    return SOURCE_KEY;
  }

  @Override
  public boolean serves(String jurisdiction) {
    return "QC".equals(jurisdiction);
  }

  @Override
  public String datasetUrl(String jurisdiction) {
    return datasetUrl;
  }

  @Override
  public LawSnapshot fetch(
      String jurisdiction, Collection<LawTarget> targets, Optional<String> knownEtag) {
    Set<String> wanted = targets.stream().map(LawTarget::instrument).collect(Collectors.toSet());
    Download download = download(knownEtag);
    if (download.notModified()) {
      return LawSnapshot.notModified(datasetUrl, knownEtag.orElse(null));
    }
    byte[] zip = download.body();
    List<LawArticle> articles = new ArrayList<>();
    try (ZipInputStream entries = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = entries.getNextEntry()) != null) {
        Matcher match = STATUTE_ENTRY.matcher(entry.getName().replace('\\', '/'));
        if (!match.matches() || !wanted.contains(match.group(1))) {
          continue;
        }
        String language = match.group(2).toLowerCase(Locale.ROOT);
        articles.addAll(
            parser.parseQuebec(new UnclosedStream(entries), match.group(1), language, datasetUrl));
      }
    } catch (IOException e) {
      throw new IllegalStateException("the Données Québec zip could not be read", e);
    }
    return new LawSnapshot(
        articles,
        Set.of("fr", "en"),
        datasetUrl,
        download.etag(),
        Sha256.hex(zip),
        consolidatedTo(datasetUrl),
        false);
  }

  /** Asks conditionally: the publisher answers 304 when the dataset is the one already read. */
  private Download download(Optional<String> knownEtag) {
    return client
        .get()
        .uri(datasetUrl)
        .headers(headers -> knownEtag.ifPresent(headers::setIfNoneMatch))
        .exchange(
            (request, response) -> {
              if (response.getStatusCode().value() == HttpStatus.NOT_MODIFIED.value()) {
                return new Download(null, knownEtag.orElse(null), true);
              }
              if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException(
                    "the Données Québec statutes zip answered " + response.getStatusCode().value());
              }
              byte[] body = response.getBody().readAllBytes();
              if (body.length == 0) {
                throw new IllegalStateException("the Données Québec statutes zip answered empty");
              }
              return new Download(body, response.getHeaders().getETag(), false);
            });
  }

  /** The snapshot is named by its date: {@code 20261021_lois.zip} is current to 2026-10-21. */
  private static LocalDate consolidatedTo(String url) {
    Matcher match = SNAPSHOT_DATE.matcher(url);
    return match.find() ? LocalDate.parse(match.group(1), DateTimeFormatter.BASIC_ISO_DATE) : null;
  }

  /** What the publisher answered. */
  private record Download(byte[] body, String etag, boolean notModified) {

    @Override
    public boolean equals(Object other) {
      return other instanceof Download that
          && notModified == that.notModified
          && Arrays.equals(body, that.body)
          && Objects.equals(etag, that.etag);
    }

    @Override
    public int hashCode() {
      return 31 * Objects.hash(etag, notModified) + Arrays.hashCode(body);
    }

    @Override
    public String toString() {
      return "Download[body="
          + (body == null ? 0 : body.length)
          + " bytes, notModified="
          + notModified
          + "]";
    }
  }

  /** Lets the parser read one zip entry without closing the zip. */
  private static final class UnclosedStream extends FilterInputStream {
    UnclosedStream(InputStream in) {
      super(in);
    }

    @Override
    public void close() {
      // the enclosing zip stream is closed by its owner
    }
  }
}
