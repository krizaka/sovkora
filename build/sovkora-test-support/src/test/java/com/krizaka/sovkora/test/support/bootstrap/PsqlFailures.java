package com.krizaka.sovkora.test.support.bootstrap;

/**
 * The lines the Postgres image's logs carry that report a failure while its initdb files run.
 *
 * <p>{@code ERROR}/{@code FATAL} only: psql prefixes its {@code NOTICE}s the same way, and a
 * bootstrap that drops what does not exist yet emits dozens of them on an empty volume. Exactly one
 * {@code FATAL} is known and benign: the image ends its init sequence by shutting the temporary
 * init server down, which terminates the connections still open on it ("terminating connection due
 * to administrator command"). It is not a seed error and nothing else is excused.
 */
final class PsqlFailures {

  private static final String INIT_SERVER_SHUTDOWN =
      "FATAL:  terminating connection due to administrator command";

  private PsqlFailures() {}

  static String in(String log) {
    return log.lines()
        .filter(line -> line.contains("ERROR:") || line.contains("FATAL:"))
        .filter(line -> !line.contains(INIT_SERVER_SHUTDOWN))
        .reduce("", (all, line) -> all.isEmpty() ? line : all + "\n" + line);
  }
}
