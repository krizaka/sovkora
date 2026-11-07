package com.krizaka.sovkora.tools.domain.model.law;

/** How an import was started (the {@code mode} of a {@code law_import} row). */
public enum LawImportMode {
  /** The scheduled watcher, reading an official source. */
  AUTOMATED,
  /** An administrator uploaded the file; who and which file are always recorded. */
  MANUAL
}
