package com.krizaka.sovkora.tools.domain.model.law;

/** Where an import stands (the {@code status} of a {@code law_import} row). */
public enum LawImportStatus {
  /** Started, not finished. */
  RUNNING,
  /** Finished, and every change was applied. */
  SUCCEEDED,
  /** Finished with a failure: nothing was read, or at least one change was not applied. */
  FAILED
}
