package com.krizaka.sovkora.core.domain.model.research;

import java.util.List;
import java.util.Objects;

/**
 * One factual statement of a grounded answer, with what it was read from.
 *
 * @param text the statement
 * @param status verified, or to validate
 * @param reason why it is to validate, or {@code null} when verified
 * @param citations where it was read
 */
public record Claim(String text, ClaimStatus status, ClaimReason reason, List<Citation> citations) {

  /** Validates the claim. */
  public Claim {
    Objects.requireNonNull(text, "text must not be null");
    status = status == null ? ClaimStatus.TO_VALIDATE : status;
    citations = citations == null ? List.of() : List.copyOf(citations);
  }
}
