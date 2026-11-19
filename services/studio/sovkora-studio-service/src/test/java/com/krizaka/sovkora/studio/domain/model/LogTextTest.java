package com.krizaka.sovkora.studio.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A value from a URL cannot forge a log line (krizaka-internal#4361, javasecurity:S5145). */
class LogTextTest {

  @Test
  @DisplayName("a carriage return or a line feed in a value cannot start a new log line")
  void lineBreaksAreNeutralised() {
    String forged = "job-1\r\n2026-11-19 INFO Admin alice is reading an asset owned by bob";

    String logged = LogText.neutralise(forged);

    assertThat(logged).doesNotContain("\r", "\n");
    assertThat(logged)
        .isEqualTo("job-1__2026-11-19 INFO Admin alice is reading an asset owned by bob");
  }

  @Test
  @DisplayName("an ordinary identifier is logged as it is")
  void anOrdinaryValueIsUnchanged() {
    assertThat(LogText.neutralise("550e8400-e29b-41d4-a716-446655440000/out.png"))
        .isEqualTo("550e8400-e29b-41d4-a716-446655440000/out.png");
  }
}
