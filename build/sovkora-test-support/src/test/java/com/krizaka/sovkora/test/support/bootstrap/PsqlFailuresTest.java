package com.krizaka.sovkora.test.support.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PsqlFailuresTest {

  private static final String INIT_SHUTDOWN =
      "2026-11-13 17:37:20.001 UTC [48] FATAL:  terminating connection due to administrator command";

  @Test
  @DisplayName("the init server's shutdown line is not a seed failure")
  void theInitServerShutdownIsNotAFailure() {
    String log =
        "/docker-entrypoint-initdb.d/10-identity.sql\n"
            + "psql:/docker-entrypoint-initdb.d/10-identity.sql:3: NOTICE:  skipping\n"
            + INIT_SHUTDOWN
            + "\nPostgreSQL init process complete; ready for start up.\n";

    assertThat(PsqlFailures.in(log)).isEmpty();
  }

  @Test
  @DisplayName("a real ERROR and any other FATAL still fail")
  void realFailuresAreKept() {
    String log =
        INIT_SHUTDOWN
            + "\npsql:30-jobs.sql:12: ERROR:  syntax error at or near \"x\"\n"
            + "FATAL:  password authentication failed for user \"app_user\"\n";

    assertThat(PsqlFailures.in(log))
        .contains("syntax error")
        .contains("password authentication failed")
        .doesNotContain("administrator command");
  }
}
