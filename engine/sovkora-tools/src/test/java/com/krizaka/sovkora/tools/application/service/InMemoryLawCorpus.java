package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.LawCorpusStore;
import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleVersion;
import com.krizaka.sovkora.tools.domain.model.law.LawImportResult;
import com.krizaka.sovkora.tools.domain.model.law.LawImportStart;
import com.krizaka.sovkora.tools.domain.model.law.LawImportStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * The corpus in memory, with the semantics of the store port: every version is a row keyed by its
 * period, a change closes the open row, the import rows keep what they were given.
 */
final class InMemoryLawCorpus implements LawCorpusStore {

  /** One import row. */
  static final class ImportRow {
    final LawImportStart start;
    LawImportResult result;

    ImportRow(LawImportStart start) {
      this.start = start;
    }

    LawImportStatus status() {
      return result == null ? LawImportStatus.RUNNING : result.status();
    }
  }

  final Map<UUID, ImportRow> imports = new LinkedHashMap<>();
  final List<LawArticleVersion> versions = new ArrayList<>();
  String etag;
  RuntimeException readFailure;

  @Override
  public UUID beginImport(LawImportStart start) {
    UUID id = UUID.randomUUID();
    imports.put(id, new ImportRow(start));
    return id;
  }

  @Override
  public void finishImport(UUID importId, LawImportResult result) {
    imports.get(importId).result = result;
  }

  @Override
  public Optional<String> lastEtag(String sourceKey, String datasetUrl) {
    return Optional.ofNullable(etag);
  }

  @Override
  public List<LawArticleVersion> currentVersions(String jurisdiction, String instrument) {
    return versions.stream()
        .filter(v -> v.inForceTo() == null)
        .filter(
            v ->
                v.article().jurisdiction().equals(jurisdiction)
                    && v.article().instrument().equals(instrument))
        .toList();
  }

  @Override
  public void add(UUID importId, String sourceKey, LawArticle article, Instant seenAt) {
    if (open(article).isPresent()) {
      throw new IllegalStateException("already open");
    }
    versions.add(new LawArticleVersion(article, seenAt, seenAt, null, importId));
  }

  @Override
  public void replace(UUID importId, LawArticle previous, LawArticle next, Instant seenAt) {
    close(previous, next.inForceFrom());
    versions.add(new LawArticleVersion(next, seenAt, seenAt, null, importId));
  }

  @Override
  public void close(LawArticle previous, LocalDate inForceTo) {
    int index = indexOfOpen(previous);
    LawArticleVersion v = versions.get(index);
    versions.set(
        index,
        new LawArticleVersion(
            v.article(),
            v.firstSeenAt(),
            v.lastSeenAt(),
            inForceTo,
            v.importId(),
            v.recordedHash()));
  }

  /**
   * Changes the stored text of every version of an article behind the corpus's back, as a hand edit
   * of the table would: the recorded hash stays the one written with the row.
   */
  void rewriteText(String instrument, String ref, String language, String text) {
    for (int i = 0; i < versions.size(); i++) {
      LawArticleVersion v = versions.get(i);
      LawArticle a = v.article();
      if (a.instrument().equals(instrument)
          && a.ref().equals(ref)
          && a.language().equals(language)) {
        LawArticle edited =
            new LawArticle(
                a.jurisdiction(),
                a.instrument(),
                a.ref(),
                a.language(),
                a.inForceFrom(),
                text,
                a.officialUrl(),
                a.sourceUrl());
        versions.set(
            i,
            new LawArticleVersion(
                edited,
                v.firstSeenAt(),
                v.lastSeenAt(),
                v.inForceTo(),
                v.importId(),
                v.recordedHash()));
      }
    }
  }

  @Override
  public void touch(
      String jurisdiction, String instrument, Collection<LawArticle> articles, Instant seenAt) {
    for (LawArticle article : articles) {
      int index = indexOfOpen(article);
      versions.set(index, versions.get(index).seenAt(seenAt));
    }
  }

  @Override
  public Optional<LawArticleVersion> inForceOn(
      String jurisdiction, String instrument, String ref, String language, LocalDate asOf) {
    if (readFailure != null) {
      throw readFailure;
    }
    return versions.stream()
        .filter(v -> same(v.article(), jurisdiction, instrument, ref, language))
        .filter(v -> !v.article().inForceFrom().isAfter(asOf))
        .filter(v -> v.inForceTo() == null || asOf.isBefore(v.inForceTo()))
        .findFirst();
  }

  /** Every version of an article, oldest first. */
  List<LawArticleVersion> history(String instrument, String ref, String language) {
    return versions.stream()
        .filter(
            v ->
                v.article().instrument().equals(instrument)
                    && v.article().ref().equals(ref)
                    && v.article().language().equals(language))
        .sorted(java.util.Comparator.comparing(v -> v.article().inForceFrom()))
        .toList();
  }

  private Optional<LawArticleVersion> open(LawArticle article) {
    return versions.stream()
        .filter(v -> v.inForceTo() == null)
        .filter(
            v ->
                same(
                    v.article(),
                    article.jurisdiction(),
                    article.instrument(),
                    article.ref(),
                    article.language()))
        .findFirst();
  }

  private int indexOfOpen(LawArticle article) {
    return versions.indexOf(open(article).orElseThrow());
  }

  private static boolean same(
      LawArticle a, String jurisdiction, String instrument, String ref, String language) {
    return a.jurisdiction().equals(jurisdiction)
        && a.instrument().equals(instrument)
        && a.ref().equals(ref)
        && a.language().equals(language);
  }
}
