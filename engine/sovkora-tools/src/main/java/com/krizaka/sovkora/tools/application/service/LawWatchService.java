package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.LawSettings;
import com.krizaka.sovkora.tools.application.port.law.LawSource;
import com.krizaka.sovkora.tools.domain.model.law.LawImportMode;
import com.krizaka.sovkora.tools.domain.model.law.LawTarget;
import com.krizaka.sovkora.tools.domain.model.law.LawWatchReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The law watcher: runs the importers of the official sources over the instruments of {@code
 * law.import.instruments}, compares what they read with the dated, hashed corpus and turns each
 * difference into one review request (see {@link LawImportService}).
 *
 * <p>Every source read is one {@code law_import} row. Nothing runs while {@code law.corpus.enabled}
 * is false.
 */
public class LawWatchService {

  private final List<LawSource> sources;
  private final LawSettings settings;
  private final LawImportService importer;

  /**
   * Creates the watcher.
   *
   * @param sources the official sources, each serving some jurisdictions
   * @param settings the {@code law.*} settings, read at each run
   * @param importer the import of one source
   */
  public LawWatchService(List<LawSource> sources, LawSettings settings, LawImportService importer) {
    this.sources = List.copyOf(sources);
    this.settings = settings;
    this.importer = importer;
  }

  /**
   * Runs one watch: import every targeted instrument, source by source.
   *
   * @return what was unchanged, stored, requested and failed, over all the sources
   * @throws IllegalStateException when a jurisdiction has no source
   */
  public LawWatchReport run() {
    if (!settings.corpusEnabled()) {
      return LawWatchReport.EMPTY;
    }
    Map<String, List<LawTarget>> byJurisdiction = new LinkedHashMap<>();
    for (LawTarget target : settings.importInstruments()) {
      byJurisdiction.computeIfAbsent(target.jurisdiction(), j -> new ArrayList<>()).add(target);
    }
    int unchanged = 0;
    int stored = 0;
    List<String> urls = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    List<UUID> imports = new ArrayList<>();
    for (Map.Entry<String, List<LawTarget>> group : byJurisdiction.entrySet()) {
      LawWatchReport part =
          importer.importFrom(
              sourceFor(group.getKey()),
              group.getKey(),
              group.getValue(),
              LawImportMode.AUTOMATED,
              null);
      unchanged += part.unchanged();
      stored += part.stored();
      urls.addAll(part.requests());
      failures.addAll(part.failures());
      imports.addAll(part.imports());
    }
    return new LawWatchReport(unchanged, stored, urls, failures, imports);
  }

  private LawSource sourceFor(String jurisdiction) {
    return sources.stream()
        .filter(source -> source.serves(jurisdiction))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no law source serves " + jurisdiction));
  }
}
