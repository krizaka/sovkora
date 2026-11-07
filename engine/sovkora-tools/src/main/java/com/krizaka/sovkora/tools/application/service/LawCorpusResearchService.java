package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.core.domain.model.research.ReadDocument;
import com.krizaka.sovkora.tools.application.port.law.LawCorpusStore;
import com.krizaka.sovkora.tools.domain.model.law.CorpusDocument;
import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleVersion;
import com.krizaka.sovkora.tools.domain.model.law.LawTargetRef;
import com.krizaka.sovkora.tools.domain.model.research.FetchedDocument;
import com.krizaka.sovkora.tools.domain.model.research.ResearchSource;
import com.krizaka.sovkora.tools.domain.port.ResearchStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code law-corpus} research provider (ADR-089 §2): grounded research that reads the dated
 * corpus and nothing else. It makes no network call; it reads {@code law_article_version} for the
 * instruments and references a kind targets, at {@code asOf}, and offers each article as a {@code
 * research_document} (its URL is the publisher's page, its hash the article's), so that the ADR-087
 * verifier checks every quote against the very text the corpus holds.
 *
 * <p>An article with no version in force on {@code asOf} is never replaced by a later text: it is
 * reported unknown ({@code LAW_VERSION_UNKNOWN}). Nor is an article whose stored text no longer
 * gives the hash recorded with it (issue #89): it is reported unknown too, and logged without its
 * text.
 */
public class LawCorpusResearchService {

  /** The key of the research source this provider serves ({@code research_source.source_key}). */
  public static final String SOURCE_KEY = "law-corpus";

  /** The provider of that source ({@code research_source.provider}): metered as {@code CALL}. */
  public static final String PROVIDER = "law-corpus";

  /**
   * What a read of the corpus found.
   *
   * @param documents the articles in force, in the order the targets list them
   * @param unknown the {@code instrument ref} pairs with no version in force on the day
   */
  public record Read(List<CorpusDocument> documents, List<String> unknown) {}

  private final LawCorpusStore corpus;
  private final ResearchStore research;
  private final Clock clock;

  /**
   * Creates the provider.
   *
   * @param corpus the dated corpus
   * @param research where research keeps the documents it read
   * @param clock the instant a document is recorded as read (injected)
   */
  public LawCorpusResearchService(LawCorpusStore corpus, ResearchStore research, Clock clock) {
    this.corpus = Objects.requireNonNull(corpus, "LawCorpusStore");
    this.research = Objects.requireNonNull(research, "ResearchStore");
    this.clock = Objects.requireNonNull(clock, "Clock");
  }

  /**
   * Reads the articles a kind targets, as they were in force on a day. No network, no model.
   *
   * @param jurisdiction the jurisdiction
   * @param targets the instruments and references
   * @param language {@code fr} or {@code en}
   * @param asOf the day the text must be in force
   * @param maxArticles the most articles to keep; the rest are not read
   * @return the documents and the references with no version for that day
   */
  public Read read(
      String jurisdiction,
      List<LawTargetRef> targets,
      String language,
      LocalDate asOf,
      int maxArticles) {
    String juris = jurisdiction.trim().toUpperCase(Locale.ROOT);
    String lang = language.trim().toLowerCase(Locale.ROOT);
    List<CorpusDocument> documents = new ArrayList<>();
    List<String> unknown = new ArrayList<>();
    for (LawTargetRef target : targets) {
      for (String ref : target.refs()) {
        if (documents.size() + unknown.size() >= maxArticles) {
          return new Read(documents, unknown);
        }
        Optional<LawArticleVersion> version =
            corpus.inForceOn(juris, target.instrument().trim(), ref.trim(), lang, asOf);
        if (version.isPresent() && version.get().intact()) {
          documents.add(toDocument(juris, version.get()));
        } else {
          // A text that no longer gives its recorded hash is never read to a model (#89): it is
          // reported like a missing version, so the rule built on it has no evidence.
          version.ifPresent(LawStatuteLookupService::logIntegrityError);
          unknown.add(target.instrument() + " " + ref);
        }
      }
    }
    return new Read(documents, unknown);
  }

  /**
   * Records the documents under the {@code law-corpus} source, as research records what it read,
   * and returns what the verifier needs to accept a citation of them.
   *
   * @param source the {@code law-corpus} source
   * @param documents the articles read
   * @return the documents this answer read, by source, URL and hash
   */
  public List<ReadDocument> record(ResearchSource source, List<CorpusDocument> documents) {
    Instant now = clock.instant();
    Instant expires = now.plus(source.freshnessSeconds(), ChronoUnit.SECONDS);
    List<ReadDocument> read = new ArrayList<>();
    for (CorpusDocument document : documents) {
      FetchedDocument stored =
          new FetchedDocument(
              document.officialUrl(),
              document.contentHash(),
              now,
              200,
              "text/plain",
              document.text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
              document.title(),
              document.text(),
              expires);
      research.saveDocument(stored, document.officialUrl(), source.id());
      read.add(
          new ReadDocument(source.sourceKey(), document.officialUrl(), document.contentHash()));
    }
    return read;
  }

  private static CorpusDocument toDocument(String jurisdiction, LawArticleVersion version) {
    LawArticle article = version.article();
    return new CorpusDocument(
        jurisdiction,
        article.instrument(),
        article.ref(),
        article.language(),
        article.text(),
        article.contentHash().hex(),
        article.inForceFrom(),
        article.officialUrl(),
        version.importId());
  }
}
