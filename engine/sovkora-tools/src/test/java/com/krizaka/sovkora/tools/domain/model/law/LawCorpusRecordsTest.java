package com.krizaka.sovkora.tools.domain.model.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LawCorpusRecordsTest {

  private static final Instant NOW = Instant.parse("2026-10-23T06:00:00Z");

  @Test
  void a_manual_import_cannot_start_without_who_and_the_hash_of_the_file() {
    String sha = "a".repeat(64);

    assertThatThrownBy(
            () -> new LawImportStart("donnees-quebec", LawImportMode.MANUAL, "u", sha, null, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new LawImportStart("donnees-quebec", LawImportMode.MANUAL, "u", sha, " ", NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new LawImportStart("donnees-quebec", LawImportMode.MANUAL, "u", null, "me", NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new LawImportStart("donnees-quebec", LawImportMode.MANUAL, "u", sha, "me", NOW))
        .isNotNull();
    assertThat(new LawImportStart("donnees-quebec", LawImportMode.AUTOMATED, "u", null, null, NOW))
        .isNotNull();
  }

  @Test
  void a_sha256_must_be_lowercase_hex() {
    assertThatThrownBy(
            () ->
                new LawImportStart(
                    "donnees-quebec", LawImportMode.AUTOMATED, "u", "ABC", null, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_result_closes_an_import() {
    assertThatThrownBy(
            () -> new LawImportResult(LawImportStatus.RUNNING, null, null, null, 0, 0, 0, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new LawImportResult(LawImportStatus.SUCCEEDED, null, null, null, -1, 0, 0, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_whole_instrument_target_covers_every_article_a_listed_one_only_its_own() {
    assertThat(LawTarget.wholeInstrument("QC", "CCQ-1991").covers("1931")).isTrue();
    LawTarget listed = LawTarget.of("QC", "CCQ-1991", List.of("1851-1853"));
    assertThat(listed.covers("1852")).isTrue();
    assertThat(listed.covers("1931")).isFalse();
  }

  @Test
  void a_lookup_carries_a_version_exactly_when_one_was_found() {
    StatuteVersion version =
        new StatuteVersion("t", "a".repeat(64), LocalDate.of(1999, 4, 1), "https://x", null);

    assertThat(StatuteLookup.found(version).version()).isEqualTo(version);
    assertThat(StatuteLookup.unknown().version()).isNull();
    assertThatThrownBy(() -> new StatuteLookup(StatuteLookup.Outcome.FOUND, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StatuteLookup(StatuteLookup.Outcome.LAW_VERSION_UNKNOWN, version))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void the_tool_answer_carries_the_text_hash_day_url_and_import_only_when_found() {
    UUID importId = UUID.randomUUID();
    StatuteVersion version =
        new StatuteVersion("t", "a".repeat(64), LocalDate.of(1999, 4, 1), "https://x", importId);

    LookupStatuteResponse found = LookupStatuteResponse.of(StatuteLookup.found(version));
    assertThat(found.outcome()).isEqualTo("FOUND");
    assertThat(found.text()).isEqualTo("t");
    assertThat(found.contentHash()).isEqualTo("a".repeat(64));
    assertThat(found.inForceFrom()).isEqualTo(LocalDate.of(1999, 4, 1));
    assertThat(found.officialUrl()).isEqualTo("https://x");
    assertThat(found.importId()).isEqualTo(importId);

    LookupStatuteResponse unknown = LookupStatuteResponse.of(StatuteLookup.unknown());
    assertThat(unknown.outcome()).isEqualTo("LAW_VERSION_UNKNOWN");
    assertThat(unknown.text()).isNull();
    assertThat(unknown.contentHash()).isNull();
  }

  @Test
  void the_uploaded_file_is_copied_and_hashed() {
    byte[] content = "<xml/>".getBytes();
    ManualLawFile file =
        new ManualLawFile("QC", "CCQ-1991", "fr", content, "https://x", "me", null, null);
    content[0] = 'X';

    assertThat(file.content()).isEqualTo("<xml/>".getBytes());
    assertThat(file.sha256()).isEqualTo(Sha256.hex("<xml/>".getBytes()));
    assertThat(file.toString()).doesNotContain("xml/");
    assertThatThrownBy(
            () -> new ManualLawFile("QC", "CCQ-1991", "de", "x".getBytes(), "u", "me", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
