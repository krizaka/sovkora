package com.krizaka.sovkora.engine.core.domain.ports.outbound;

import com.krizaka.sovkora.engine.core.domain.model.checks.CheckAudit;

/** Where the external-check audit is written: one line per call, refused or not. */
public interface CheckAuditLog {

  /**
   * Writes one audit line.
   *
   * @param audit the line
   */
  void record(CheckAudit audit);
}
