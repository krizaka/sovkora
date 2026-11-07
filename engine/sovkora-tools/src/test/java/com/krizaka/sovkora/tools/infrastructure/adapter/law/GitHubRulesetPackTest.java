package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import static com.krizaka.sovkora.tools.infrastructure.adapter.law.RecordedGitHub.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.krizaka.sovkora.tools.domain.model.law.GoldenCase;
import com.krizaka.sovkora.tools.domain.model.law.PackKind;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The pack read on a recorded GitHub contents API. */
class GitHubRulesetPackTest {

  private final RestClient.Builder builder = RestClient.builder().baseUrl(API);
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final GitHubRulesetPack pack =
      new GitHubRulesetPack(builder.build(), "krizaka", "sovkora-packs", "main");

  @Test
  void a_kind_gives_its_jurisdiction_its_targets_and_its_questions() {
    server
        .expect(
            requestTo(REPO + "/contents/document-validation/kinds/qc-visit-notice.json?ref=main"))
        .andRespond(withSuccess(answer("contents-kind.json"), MediaType.APPLICATION_JSON));

    PackKind kind = pack.kind("qc-visit-notice").orElseThrow();

    assertThat(kind.jurisdiction()).isEqualTo("QC");
    assertThat(kind.status()).isEqualTo("KNOWN");
    assertThat(kind.targets())
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.instrument()).isEqualTo("CCQ-1991");
              assertThat(t.refs()).containsExactly("1931", "1932", "1933");
            });
    assertThat(kind.questions()).containsKeys("fr", "en");
  }

  @Test
  void an_absent_kind_is_empty() {
    server
        .expect(
            requestTo(REPO + "/contents/document-validation/kinds/qc-sublet-notice.json?ref=main"))
        .andRespond(withResourceNotFound());

    assertThat(pack.kind("qc-sublet-notice")).isEmpty();
  }

  @Test
  void the_golden_cases_are_the_documents_of_the_fixtures_folder() {
    server
        .expect(
            requestTo(
                REPO + "/contents/document-validation/worker/fixtures/qc-visit-notice?ref=main"))
        .andRespond(withSuccess(answer("contents-golden.json"), MediaType.APPLICATION_JSON));

    assertThat(pack.goldenCases("qc-visit-notice"))
        .extracting(GoldenCase::name)
        .containsExactly("fr-compliant", "fr-defective", "fr-blank");
  }

  @Test
  void a_kind_with_no_fixtures_folder_has_no_golden_case() {
    server
        .expect(
            requestTo(
                REPO + "/contents/document-validation/worker/fixtures/qc-sublet-notice?ref=main"))
        .andRespond(withResourceNotFound());

    assertThat(pack.goldenCases("qc-sublet-notice")).isEmpty();
  }

  @Test
  void the_revision_of_an_existing_version_is_read_and_a_new_version_has_none() {
    server
        .expect(
            requestTo(
                REPO
                    + "/contents/document-validation/rulesets/qc-visit-notice/1999-04-01.json?ref=main"))
        .andRespond(withSuccess(answer("contents-ruleset.json"), MediaType.APPLICATION_JSON));
    server
        .expect(
            requestTo(
                REPO
                    + "/contents/document-validation/rulesets/qc-visit-notice/2026-01-01.json?ref=main"))
        .andRespond(withResourceNotFound());

    assertThat(pack.rulesetRevision("qc-visit-notice", LocalDate.parse("1999-04-01"))).hasValue(2);
    assertThat(pack.rulesetRevision("qc-visit-notice", LocalDate.parse("2026-01-01"))).isEmpty();
  }

  @Test
  void a_kind_id_never_names_another_path() {
    assertThatThrownBy(() -> pack.kind("../../.github/workflows/ci"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> pack.goldenCases("qc-visit-notice/../../x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
