package com.krizaka.sovkora.tools.domain.model.law;

import java.util.List;
import java.util.Objects;

/**
 * What {@code vet} found on one rule: no finding means it passed. Stored as the proposal's {@code
 * vet_report}.
 *
 * @param vetVersion the version of the checks that wrote it
 * @param asOf the day the articles were read at, ISO 8601
 * @param findings the reasons, in the order the checks ran; empty when it passed
 */
public record VetReport(String vetVersion, String asOf, List<VetFinding> findings) {

  /** Copies the findings. */
  public VetReport {
    Objects.requireNonNull(vetVersion, "vetVersion");
    Objects.requireNonNull(asOf, "asOf");
    findings = findings == null ? List.of() : List.copyOf(findings);
  }

  /**
   * Whether the rule passed every check.
   *
   * @return true when there is no finding
   */
  public boolean passed() {
    return findings.isEmpty();
  }

  /**
   * The reasons, without their details.
   *
   * @return the reasons in order, each once
   */
  public List<VetReason> reasons() {
    return findings.stream().map(VetFinding::reason).distinct().toList();
  }
}
