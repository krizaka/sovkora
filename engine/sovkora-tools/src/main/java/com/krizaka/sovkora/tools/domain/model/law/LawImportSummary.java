package com.krizaka.sovkora.tools.domain.model.law;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One {@code law_import} row: what was read, its hash, and what it changed.
 *
 * @param id the row id
 * @param sourceKey the source
 * @param mode {@code AUTOMATED} or {@code MANUAL}
 * @param datasetUrl where the file came from
 * @param datasetSha256 the SHA-256 of the file
 * @param consolidatedTo the day the publisher consolidated it to
 * @param importedBy who imported it (manual imports)
 * @param startedAt when it started
 * @param finishedAt when it ended, if it did
 * @param articlesNew articles stored for the first time
 * @param articlesChanged articles with a new version
 * @param articlesUnchanged articles confirmed
 * @param status {@code RUNNING}, {@code SUCCEEDED} or {@code FAILED}
 */
public record LawImportSummary(
    UUID id,
    String sourceKey,
    String mode,
    String datasetUrl,
    String datasetSha256,
    LocalDate consolidatedTo,
    String importedBy,
    Instant startedAt,
    Instant finishedAt,
    int articlesNew,
    int articlesChanged,
    int articlesUnchanged,
    String status) {}
