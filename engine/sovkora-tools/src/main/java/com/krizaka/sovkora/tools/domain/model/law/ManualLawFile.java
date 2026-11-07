package com.krizaka.sovkora.tools.domain.model.law;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

/**
 * A consolidated statute uploaded by an administrator, the traceable fallback when an automated
 * source cannot be read (ADR-089 §2).
 *
 * @param jurisdiction {@code CA} or {@code QC}
 * @param instrument the instrument code the file holds
 * @param language {@code fr} or {@code en}
 * @param content the XML, as downloaded
 * @param sourceUrl the address the file came from
 * @param importedBy who uploaded it (required)
 * @param declaredSha256 the digest the administrator declares for the file; checked against the
 *     file, and the file's own digest is recorded in any case; may be null
 * @param consolidatedTo the day the consolidation is current to, when the administrator knows; may
 *     be null
 */
public record ManualLawFile(
    String jurisdiction,
    String instrument,
    String language,
    byte[] content,
    String sourceUrl,
    String importedBy,
    String declaredSha256,
    LocalDate consolidatedTo) {

  /** Validates the upload: who, which file and its origin are all required. */
  public ManualLawFile {
    Objects.requireNonNull(jurisdiction, "jurisdiction");
    Objects.requireNonNull(instrument, "instrument");
    Objects.requireNonNull(content, "content");
    if (!"fr".equals(language) && !"en".equals(language)) {
      throw new IllegalArgumentException("language must be fr or en");
    }
    if (content.length == 0) {
      throw new IllegalArgumentException("the file is empty");
    }
    if (sourceUrl == null || sourceUrl.isBlank()) {
      throw new IllegalArgumentException("the address the file came from is required");
    }
    if (importedBy == null || importedBy.isBlank()) {
      throw new IllegalArgumentException("who imported the file is required");
    }
    content = content.clone();
  }

  /**
   * The file.
   *
   * @return a copy of the bytes
   */
  @Override
  public byte[] content() {
    return content.clone();
  }

  /**
   * The SHA-256 of the file, which the import row records.
   *
   * @return the digest in lowercase hex
   */
  public String sha256() {
    return Sha256.hex(content);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ManualLawFile that
        && jurisdiction.equals(that.jurisdiction)
        && instrument.equals(that.instrument)
        && language.equals(that.language)
        && Arrays.equals(content, that.content)
        && sourceUrl.equals(that.sourceUrl)
        && importedBy.equals(that.importedBy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jurisdiction, instrument, language, Arrays.hashCode(content), sourceUrl);
  }

  @Override
  public String toString() {
    return "ManualLawFile[" + jurisdiction + ":" + instrument + " " + language + "]";
  }
}
