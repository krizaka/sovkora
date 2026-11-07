package com.krizaka.sovkora.tools.domain.model.law;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One version of an article, without its text: the text leaves the corpus only through the checked,
 * metered read.
 *
 * @param inForceFrom the day it took effect
 * @param inForceTo the day it stopped, or null while in force
 * @param contentHash the SHA-256 of its text
 * @param officialUrl the publisher's page
 * @param importId the import that brought it in
 * @param length the length of its text, in characters
 */
public record LawVersionSummary(
    LocalDate inForceFrom,
    LocalDate inForceTo,
    String contentHash,
    String officialUrl,
    UUID importId,
    int length) {

  /**
   * Whether this version is the one in force on a day.
   *
   * @param day the day
   * @return true when {@code inForceFrom <= day < inForceTo}
   */
  public boolean inForceOn(LocalDate day) {
    return !inForceFrom.isAfter(day) && (inForceTo == null || day.isBefore(inForceTo));
  }
}
