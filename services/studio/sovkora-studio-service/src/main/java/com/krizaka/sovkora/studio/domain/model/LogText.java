package com.krizaka.sovkora.studio.domain.model;

/**
 * Neutralises a value a caller chose before it is written to a log (krizaka-internal#4361).
 *
 * <p>A job id or a file name taken from a URL could carry a carriage return or a line feed, and
 * with it a whole forged log line ("Admin … is reading an asset owned by …"). Both are replaced by
 * {@code _}, so one logged event stays one line. A dependency-free pure function (AGENTS.md §4,
 * ERR-127 b).
 */
public final class LogText {

  private LogText() {}

  /**
   * The value with every carriage return and line feed replaced by {@code _}.
   *
   * @param value a value the caller chose
   * @return the value, safe to log on one line
   */
  public static String neutralise(String value) {
    return value.replaceAll("[\n\r]", "_");
  }
}
