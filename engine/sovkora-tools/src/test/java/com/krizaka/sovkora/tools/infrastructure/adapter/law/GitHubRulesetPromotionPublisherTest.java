package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import static com.krizaka.sovkora.tools.infrastructure.adapter.law.RecordedGitHub.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.krizaka.sovkora.tools.application.port.law.RulesetPromotionPublisher.Opened;
import com.krizaka.sovkora.tools.domain.model.law.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The promotion pull request on a recorded GitHub API: one commit on a new branch with the ruleset,
 * the pinned article and the kind set to {@code PROMOTED}, then the pull request against {@code
 * main}. The base branch is read, never written.
 */
class GitHubRulesetPromotionPublisherTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final LocalDate IN_FORCE = LocalDate.parse("1999-04-01");
  private static final UUID IMPORT = UUID.fromString("00000000-0000-4000-8000-000000001931");
  private static final UUID PROPOSAL = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID RUN = UUID.fromString("00000000-0000-4000-8000-000000000089");

  private final List<MockClientHttpRequest> sent = new ArrayList<>();

  private static RulesetPromotion promotion() {
    LawArticle a1931 =
        DerivationFixtures.visitArticles().stream()
            .filter(a -> "1931".equals(a.ref()))
            .findFirst()
            .orElseThrow();
    ProposedRule rule = DerivationFixtures.visitAnswers().get("1931").getFirst();
    LawBlock block = rule.law().getFirst();
    PinnedLaw pinned =
        new PinnedLaw(
            block.instrument(),
            block.ref(),
            block.language(),
            IN_FORCE,
            block.contentHash(),
            a1931.officialUrl(),
            block.quote());
    PinnedArticle article =
        new PinnedArticle(
            "CCQ-1991",
            "1931",
            "fr",
            IN_FORCE,
            block.contentHash(),
            a1931.officialUrl(),
            a1931.text(),
            IMPORT);
    return new RulesetPromotion(
        "qc-visit-notice",
        "QC",
        IN_FORCE,
        1,
        null,
        List.of(new PromotedRule(rule, List.of(pinned))),
        List.of(article),
        new RulesetPromotion.Provenance(
            List.of(RUN),
            List.of(PROPOSAL),
            List.of("eric-admin"),
            Instant.parse("2026-10-24T12:00:00Z"),
            List.of(IMPORT)));
  }

  @Test
  @DisplayName("one commit on a new branch and one pull request; main is never written")
  void a_promotion_is_a_branch_one_commit_and_a_pull_request() {
    RestClient.Builder builder = RestClient.builder().baseUrl(API);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    expect(
        server,
        HttpMethod.GET,
        REPO + "/contents/document-validation/kinds/qc-visit-notice.json?ref=main",
        "contents-kind.json");
    expect(server, HttpMethod.GET, REPO + "/git/ref/heads/main", "ref-main.json");
    expect(
        server,
        HttpMethod.GET,
        REPO + "/git/commits/base0000000000000000000000000000000000c0",
        "commit-base.json");
    expect(server, HttpMethod.POST, REPO + "/git/trees", "tree-created.json");
    expect(server, HttpMethod.POST, REPO + "/git/commits", "commit-created.json");
    expect(server, HttpMethod.POST, REPO + "/git/refs", "ref-created.json");
    expect(server, HttpMethod.POST, REPO + "/pulls", "pull-created.json");
    GitHubRulesetPromotionPublisher publisher =
        new GitHubRulesetPromotionPublisher(
            builder.build(),
            "krizaka",
            "sovkora-packs",
            "main",
            () -> UUID.fromString("0000abcd-0000-4000-8000-000000000000"));

    Opened opened = publisher.open(promotion());

    server.verify();
    assertThat(opened.prUrl()).isEqualTo("https://github.com/krizaka/sovkora-packs/pull/4242");
    assertThat(opened.branch()).isEqualTo("law-promote/qc-visit-notice/1999-04-01-r1-0000abcd");

    JsonNode tree = body(3);
    assertThat(tree.path("base_tree").asString())
        .isEqualTo("tree0000000000000000000000000000000000t0");
    Map<String, String> files = new HashMap<>();
    tree.path("tree")
        .forEach(
            entry -> files.put(entry.path("path").asString(), entry.path("content").asString()));
    assertThat(files)
        .containsOnlyKeys(
            "document-validation/rulesets/qc-visit-notice/1999-04-01.json",
            "document-validation/law/CCQ-1991-1931-fr-1999-04-01.json",
            "document-validation/kinds/qc-visit-notice.json");

    JsonNode ruleset =
        JSON.readTree(files.get("document-validation/rulesets/qc-visit-notice/1999-04-01.json"));
    assertThat(ruleset.path("rulesetId").asString()).isEqualTo("qc-visit-notice");
    assertThat(ruleset.path("version").asString()).isEqualTo("1999-04-01");
    assertThat(ruleset.path("revision").asInt()).isEqualTo(1);
    assertThat(ruleset.path("reviewStatus").asString()).isEqualTo("REVIEWED_INTERNAL");
    assertThat(ruleset.path("reviewNote").asString()).contains("TO_REVIEW_BY_LAWYER");
    JsonNode law = ruleset.path("rules").get(0).path("law").get(0);
    assertThat(law.path("inForceFrom").asString()).isEqualTo("1999-04-01");
    assertThat(law.path("officialUrl").asString())
        .startsWith("https://www.legisquebec.gouv.qc.ca/");
    assertThat(law.path("contentHash").asString()).hasSize(64);
    JsonNode provenance = ruleset.path("provenance");
    assertThat(provenance.path("proposalIds").get(0).asString()).isEqualTo(PROPOSAL.toString());
    assertThat(provenance.path("derivationRunIds").get(0).asString()).isEqualTo(RUN.toString());
    assertThat(provenance.path("lawImportIds").get(0).asString()).isEqualTo(IMPORT.toString());
    assertThat(provenance.path("reviewedBy").get(0).asString()).isEqualTo("eric-admin");

    JsonNode pinned =
        JSON.readTree(files.get("document-validation/law/CCQ-1991-1931-fr-1999-04-01.json"));
    assertThat(pinned.path("text").asString()).startsWith("1931 Le locateur");
    JsonNode kind = JSON.readTree(files.get("document-validation/kinds/qc-visit-notice.json"));
    assertThat(kind.path("status").asString()).isEqualTo("PROMOTED");
    assertThat(kind.path("ruleset").asString()).isEqualTo("qc-visit-notice");
    assertThat(kind.path("signals").size()).as("the rest of the kind is untouched").isEqualTo(4);
    assertThat(files.get("document-validation/kinds/qc-visit-notice.json"))
        .startsWith("{\n  \"$schema\": \"./kind.schema.json\",\n")
        .endsWith("}\n");

    assertThat(body(4).path("parents").get(0).asString())
        .isEqualTo("base0000000000000000000000000000000000c0");
    assertThat(body(5).path("ref").asString())
        .isEqualTo("refs/heads/law-promote/qc-visit-notice/1999-04-01-r1-0000abcd");
    JsonNode pull = body(6);
    assertThat(pull.path("base").asString()).isEqualTo("main");
    assertThat(pull.path("head").asString()).isEqualTo(opened.branch());
    assertThat(pull.path("body").asString())
        .contains("CCQ-1991 art. 1931")
        .contains("TO_REVIEW_BY_LAWYER");
    assertThat(sent)
        .noneMatch(r -> r.getMethod() == HttpMethod.PATCH)
        .noneMatch(
            r ->
                r.getURI().toString().endsWith("/git/refs/heads/main")
                    && r.getMethod() != HttpMethod.GET);
  }

  private void expect(MockRestServiceServer server, HttpMethod verb, String url, String answer) {
    server
        .expect(requestTo(url))
        .andExpect(method(verb))
        .andExpect(request -> sent.add((MockClientHttpRequest) request))
        .andRespond(withSuccess(answer(answer), MediaType.APPLICATION_JSON));
  }

  private JsonNode body(int index) {
    return JSON.readTree(sent.get(index).getBodyAsString(StandardCharsets.UTF_8));
  }
}
