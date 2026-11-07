package com.krizaka.sovkora.tools.domain.model.law;

import java.util.List;
import java.util.UUID;

/**
 * The outcome of one watch run.
 *
 * @param unchanged articles confirmed with the same hash
 * @param stored articles stored for the first time, with no request (first import of an instrument)
 * @param requests URLs of the review requests opened
 * @param failures one line per change whose request could not be opened; it stays unapplied and is
 *     detected again by the next run
 * @param imports the {@code law_import} rows this run wrote, one per source read
 */
public record LawWatchReport(
    int unchanged, int stored, List<String> requests, List<String> failures, List<UUID> imports) {

  /** The report of a run that did nothing, such as one with the corpus switched off. */
  public static final LawWatchReport EMPTY =
      new LawWatchReport(0, 0, List.of(), List.of(), List.of());

  /** Copies the lists. */
  public LawWatchReport {
    requests = List.copyOf(requests);
    failures = List.copyOf(failures);
    imports = List.copyOf(imports);
  }
}
