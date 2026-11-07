package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.LawChangeRequests;
import com.krizaka.sovkora.tools.application.port.law.LawCorpusStore;
import com.krizaka.sovkora.tools.application.port.law.LawSettings;
import com.krizaka.sovkora.tools.application.port.law.LawSource;
import com.krizaka.sovkora.tools.domain.model.law.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * One import of the law corpus, whoever starts it (the scheduled watcher or an administrator's
 * upload): one {@code law_import} row, then the comparison of what the source holds with the
 * corpus.
 *
 * <p>The first import of an instrument and language only stores its texts (there is nothing to
 * compare with). After that an added, changed or removed article opens one review request, and the
 * change is applied only once the request is open, so a failed request is detected again by the
 * next import. Without a request port (no watcher), changes are applied directly: the import row
 * says who imported which file. A changed text closes the previous version and stores the new one
 * as a new dated row, in one transaction; a reverted text is a new dated row too.
 */
public class LawImportService {

  private final LawCorpusStore store;
  private final LawSettings settings;
  private final LawChangeRequests requests;
  private final Clock clock;

  /**
   * Creates the importer.
   *
   * @param store the corpus
   * @param settings the {@code law.*} settings
   * @param requests where the changes are reviewed; null when no review is wired
   * @param clock the clock of the run (injected, never read from the system)
   */
  public LawImportService(
      LawCorpusStore store, LawSettings settings, LawChangeRequests requests, Clock clock) {
    this.store = store;
    this.settings = settings;
    this.requests = requests;
    this.clock = clock;
  }

  /**
   * Imports the targets of one jurisdiction from a source.
   *
   * @param source where the texts are read
   * @param jurisdiction the jurisdiction, for which the source serves
   * @param targets the instruments to store
   * @param mode how the import was started
   * @param importedBy who started a manual import; null for an automated one
   * @return what was unchanged, stored, requested and failed, and the import row written
   */
  public LawWatchReport importFrom(
      LawSource source,
      String jurisdiction,
      List<LawTarget> targets,
      LawImportMode mode,
      String importedBy) {
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, clock.getZone());
    UUID importId =
        store.beginImport(
            new LawImportStart(
                source.sourceKey(),
                mode,
                source.datasetUrl(jurisdiction),
                source.knownSha256().orElse(null),
                importedBy,
                now));
    Run run = new Run(importId, source.sourceKey(), now, today);
    LawSnapshot snapshot = null;
    try {
      snapshot =
          source.fetch(jurisdiction, targets, knownEtag(source, jurisdiction, targets, mode));
      if (!snapshot.notModified()) {
        checkLengths(snapshot);
        for (LawTarget target : targets) {
          importTarget(run, target, snapshot);
        }
      }
    } catch (RuntimeException e) {
      run.failures.add("source " + source.sourceKey() + ": " + failureOf(e));
    }
    store.finishImport(importId, result(run, snapshot));
    return new LawWatchReport(
        run.unchanged, run.stored, run.requestUrls, run.failures, List.of(importId));
  }

  private Optional<String> knownEtag(
      LawSource source, String jurisdiction, List<LawTarget> targets, LawImportMode mode) {
    boolean everyInstrumentImported =
        targets.stream()
            .allMatch(
                target ->
                    !store.currentVersions(target.jurisdiction(), target.instrument()).isEmpty());
    if (mode != LawImportMode.AUTOMATED || !everyInstrumentImported) {
      // A dataset that did not change can only be skipped if it was imported whole before.
      return Optional.empty();
    }
    return store.lastEtag(source.sourceKey(), source.datasetUrl(jurisdiction));
  }

  private void checkLengths(LawSnapshot snapshot) {
    int max = settings.maxArticleChars();
    for (LawArticle article : snapshot.articles()) {
      if (article.text().length() > max) {
        throw new IllegalStateException(
            "article "
                + article.instrument()
                + " "
                + article.ref()
                + " ("
                + article.language()
                + ") has "
                + article.text().length()
                + " characters, the limit is "
                + max
                + " (law.import.max-article-chars); nothing was imported");
      }
    }
  }

  private void importTarget(Run run, LawTarget target, LawSnapshot snapshot) {
    Plan plan;
    try {
      plan = reconcile(run, target, snapshot);
    } catch (IllegalStateException e) {
      run.failures.add("target " + target.instrument() + ": " + e.getMessage());
      return;
    }
    run.failures.addAll(plan.failures());
    run.unchanged += plan.unchanged().size();
    store.touch(target.jurisdiction(), target.instrument(), plan.unchanged(), run.now);
    for (LawArticle article : plan.toStore()) {
      try {
        store.add(run.importId, run.sourceKey, article, run.now);
        run.stored++;
        run.articlesNew++;
      } catch (RuntimeException e) {
        run.failures.add(describe(LawChange.Kind.ADDED, article) + ": " + failureOf(e));
      }
    }
    for (LawChange change : plan.changes()) {
      try {
        if (requests != null) {
          run.requestUrls.add(requests.open(change));
        }
        apply(run, change);
      } catch (RuntimeException e) {
        run.failures.add(describe(change.kind(), change.subject()) + ": " + failureOf(e));
      }
    }
  }

  private Plan reconcile(Run run, LawTarget target, LawSnapshot snapshot) {
    Set<String> languages = snapshot.languages();
    Map<String, LawArticleVersion> current = new LinkedHashMap<>();
    Set<String> knownLanguages = new HashSet<>();
    for (LawArticleVersion version :
        store.currentVersions(target.jurisdiction(), target.instrument())) {
      LawArticle stored = version.article();
      knownLanguages.add(stored.language());
      if (languages.contains(stored.language()) && target.covers(stored.ref())) {
        current.put(key(stored), version);
      }
    }
    Set<String> incomingLanguages = new HashSet<>();
    List<LawChange> changes = new ArrayList<>();
    List<LawArticle> toStore = new ArrayList<>();
    List<LawArticle> unchanged = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (LawArticle article : snapshot.articles()) {
      if (!article.instrument().equals(target.instrument()) || !target.covers(article.ref())) {
        continue;
      }
      incomingLanguages.add(article.language());
      String key = key(article);
      seen.add(key);
      LawArticleVersion previous = current.get(key);
      if (previous == null) {
        if (knownLanguages.contains(article.language())) {
          changes.add(new LawChange(LawChange.Kind.ADDED, null, article, run.today));
        } else {
          toStore.add(article);
        }
      } else if (previous.article().contentHash().equals(article.contentHash())) {
        unchanged.add(article);
      } else if (!article.inForceFrom().isAfter(previous.article().inForceFrom())) {
        failures.add(
            describe(LawChange.Kind.CHANGED, article)
                + ": the text changed but not its in-force date, nothing was changed");
      } else {
        changes.add(new LawChange(LawChange.Kind.CHANGED, previous.article(), article, run.today));
      }
    }
    for (String language : languages) {
      boolean stored =
          current.values().stream().anyMatch(v -> v.article().language().equals(language));
      if (stored && !incomingLanguages.contains(language)) {
        // An empty answer must not flag the whole instrument as removed: a human looks first.
        throw new IllegalStateException(
            "the source returned no "
                + language
                + " article of "
                + target.instrument()
                + ", nothing was changed");
      }
    }
    for (Map.Entry<String, LawArticleVersion> entry : current.entrySet()) {
      if (!seen.contains(entry.getKey())) {
        changes.add(
            new LawChange(LawChange.Kind.REMOVED, entry.getValue().article(), null, run.today));
      }
    }
    return new Plan(unchanged, toStore, changes, failures);
  }

  private void apply(Run run, LawChange change) {
    switch (change.kind()) {
      case ADDED -> {
        store.add(run.importId, run.sourceKey, change.current(), run.now);
        run.articlesNew++;
      }
      case CHANGED -> {
        store.replace(run.importId, change.previous(), change.current(), run.now);
        run.articlesChanged++;
      }
      case REMOVED -> {
        LocalDate day =
            change.detectedOn().isBefore(change.previous().inForceFrom())
                ? change.previous().inForceFrom()
                : change.detectedOn();
        store.close(change.previous(), day);
        run.articlesChanged++;
      }
    }
  }

  private LawImportResult result(Run run, LawSnapshot snapshot) {
    LawImportStatus status =
        run.failures.isEmpty() ? LawImportStatus.SUCCEEDED : LawImportStatus.FAILED;
    return new LawImportResult(
        status,
        snapshot == null ? null : snapshot.datasetEtag(),
        snapshot == null ? null : snapshot.datasetSha256(),
        snapshot == null ? null : snapshot.consolidatedTo(),
        run.articlesNew,
        run.articlesChanged,
        run.unchanged,
        clock.instant());
  }

  private static String describe(LawChange.Kind kind, LawArticle article) {
    return kind
        + " "
        + article.instrument()
        + " "
        + article.ref()
        + " ("
        + article.language()
        + ")";
  }

  /** The reason of a failure, without anything the source or the user wrote. */
  private static String failureOf(RuntimeException e) {
    return e instanceof IllegalStateException && e.getMessage() != null
        ? e.getMessage()
        : e.getClass().getSimpleName();
  }

  private static String key(LawArticle article) {
    return article.instrument() + "|" + article.ref() + "|" + article.language();
  }

  /** The mutable tally of one import. */
  private static final class Run {
    private final UUID importId;
    private final String sourceKey;
    private final Instant now;
    private final LocalDate today;
    private final List<String> requestUrls = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private int unchanged;
    private int stored;
    private int articlesNew;
    private int articlesChanged;

    private Run(UUID importId, String sourceKey, Instant now, LocalDate today) {
      this.importId = importId;
      this.sourceKey = sourceKey;
      this.now = now;
      this.today = today;
    }
  }

  /** The decisions of one target: what is confirmed, new on a first import, moved, or refused. */
  private record Plan(
      List<LawArticle> unchanged,
      List<LawArticle> toStore,
      List<LawChange> changes,
      List<String> failures) {}
}
