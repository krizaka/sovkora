package com.krizaka.sovkora.tools.domain.model.law;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The end of one import, written on its {@code law_import} row.
 *
 * @param status {@link LawImportStatus#SUCCEEDED} or {@link LawImportStatus#FAILED}
 * @param datasetEtag the validator the publisher sent, when there is one
 * @param datasetSha256 the SHA-256 of the dataset or file read; null when nothing was read
 * @param consolidatedTo the day the consolidation is current to, when the source says
 * @param articlesNew versions stored for an article never seen before
 * @param articlesChanged versions that took over from another one, and removals
 * @param articlesUnchanged articles confirmed with the same hash
 * @param finishedAt when it finished
 */
public record LawImportResult(
    LawImportStatus status,
    String datasetEtag,
    String datasetSha256,
    LocalDate consolidatedTo,
    int articlesNew,
    int articlesChanged,
    int articlesUnchanged,
    Instant finishedAt) {

  /** Validates the record: a finished import is neither running nor negative. */
  public LawImportResult {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(finishedAt, "finishedAt");
    if (status == LawImportStatus.RUNNING) {
      throw new IllegalArgumentException("a result closes an import");
    }
    if (articlesNew < 0 || articlesChanged < 0 || articlesUnchanged < 0) {
      throw new IllegalArgumentException("counts cannot be negative");
    }
  }
}
