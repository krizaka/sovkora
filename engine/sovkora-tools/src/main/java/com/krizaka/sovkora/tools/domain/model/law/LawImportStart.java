package com.krizaka.sovkora.tools.domain.model.law;

import java.time.Instant;
import java.util.Objects;

/**
 * The opening of one import: what is recorded before the source is read, so that a failed import
 * leaves its row.
 *
 * @param sourceKey the {@code research_source} the text comes from
 * @param mode how the import was started
 * @param datasetUrl the dataset or file address, when known
 * @param datasetSha256 the SHA-256 of the file, known up front for a manual import
 * @param importedBy who started it; required for a manual import
 * @param startedAt when it started
 */
public record LawImportStart(
    String sourceKey,
    LawImportMode mode,
    String datasetUrl,
    String datasetSha256,
    String importedBy,
    Instant startedAt) {

  /** Validates the record: a manual import names who did it and the hash of the file. */
  public LawImportStart {
    Objects.requireNonNull(sourceKey, "sourceKey");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(startedAt, "startedAt");
    if (datasetSha256 != null && !datasetSha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("a SHA-256 digest in lowercase hex is expected");
    }
    if (mode == LawImportMode.MANUAL
        && (importedBy == null || importedBy.isBlank() || datasetSha256 == null)) {
      throw new IllegalArgumentException(
          "a manual import records who imported it and the SHA-256 of the file");
    }
  }
}
