package com.krizaka.sovkora.tools.domain.model.law;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored version of an article. {@code inForceTo} is null while it is the current version.
 *
 * @param article the article as it was read
 * @param firstSeenAt when this exact text was first read
 * @param lastSeenAt when it was last confirmed by an import
 * @param inForceTo the day the next version took over, or the day it was removed; null if current
 * @param importId the import that stored this version; null before it is stored
 * @param recordedHash the {@code content_hash} written with the row, in lowercase hex: what the
 *     text must still hash to for the version to be served (issue #89)
 */
public record LawArticleVersion(
    LawArticle article,
    Instant firstSeenAt,
    Instant lastSeenAt,
    LocalDate inForceTo,
    UUID importId,
    String recordedHash) {

  /**
   * Requires a recorded hash. Its form is not checked here: a row whose {@code content_hash} is not
   * a digest is a corrupted row, so it is read as a version that is not {@link #intact()} (served
   * as {@code LAW_INTEGRITY_ERROR}, with no text) rather than as an error of the caller's question.
   */
  public LawArticleVersion {
    Objects.requireNonNull(recordedHash, "recordedHash");
  }

  /**
   * A version of an article just read from a source, before it is stored: the hash it records is
   * its own. A store reading a row back never uses this; it passes the row's {@code content_hash}.
   *
   * @param article the article as it was read
   * @param firstSeenAt when this exact text was first read
   * @param lastSeenAt when it was last confirmed by an import
   * @param inForceTo the day the next version took over; null if current
   * @param importId the import that stored this version; null before it is stored
   */
  public LawArticleVersion(
      LawArticle article,
      Instant firstSeenAt,
      Instant lastSeenAt,
      LocalDate inForceTo,
      UUID importId) {
    this(article, firstSeenAt, lastSeenAt, inForceTo, importId, article.contentHash().hex());
  }

  /**
   * Whether the text, normalised again by the corpus's own rule ({@link ContentHash}), still gives
   * the hash recorded with the row. A version that is not intact is never served: its text was
   * changed after it was imported.
   *
   * @return true when the recomputed hash is the recorded one
   */
  public boolean intact() {
    return article.contentHash().hex().equals(recordedHash);
  }

  /**
   * Returns the same version confirmed again at {@code seenAt}.
   *
   * @param seenAt the import instant
   * @return a copy with the new {@code lastSeenAt}
   */
  public LawArticleVersion seenAt(Instant seenAt) {
    return new LawArticleVersion(article, firstSeenAt, seenAt, inForceTo, importId, recordedHash);
  }
}
