package com.krizaka.sovkora.tools.domain.model.law;

import java.util.Objects;

/**
 * One reason {@code vet} gave, with what it saw. The detail names parts of the rule and of the law,
 * never a user's document: a derivation reads no document.
 *
 * @param reason the reason
 * @param detail what was checked and failed, for the reviewer
 */
public record VetFinding(VetReason reason, String detail) {

  /** Checks the reason is named. */
  public VetFinding {
    Objects.requireNonNull(reason, "reason");
    detail = detail == null ? "" : detail;
  }
}
