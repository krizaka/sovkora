package com.krizaka.sovkora.tools.domain.model.law;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload of the {@code lookupStatute} tool.
 *
 * @param outcome {@code FOUND}, {@code LAW_VERSION_UNKNOWN}, {@code LAW_CORPUS_DISABLED}, {@code
 *     LAW_INTEGRITY_ERROR} or {@code INVALID_REQUEST}
 * @param text the article text; only when found
 * @param contentHash the SHA-256 of the text; only when found
 * @param inForceFrom the day the text took effect; only when found
 * @param officialUrl the publisher's page, to cite; only when found
 * @param importId the import that brought the text in; only when found
 */
public record LookupStatuteResponse(
    String outcome,
    String text,
    String contentHash,
    LocalDate inForceFrom,
    String officialUrl,
    UUID importId) {

  /** The tool's answer for a lookup. */
  public static LookupStatuteResponse of(StatuteLookup lookup) {
    StatuteVersion version = lookup.version();
    return version == null
        ? new LookupStatuteResponse(lookup.outcome().name(), null, null, null, null, null)
        : new LookupStatuteResponse(
            lookup.outcome().name(),
            version.text(),
            version.contentHash(),
            version.inForceFrom(),
            version.officialUrl(),
            version.importId());
  }

  /**
   * The answer to a question the tool cannot read.
   *
   * @return an {@code INVALID_REQUEST} answer with nothing else
   */
  public static LookupStatuteResponse invalid() {
    return new LookupStatuteResponse("INVALID_REQUEST", null, null, null, null, null);
  }
}
