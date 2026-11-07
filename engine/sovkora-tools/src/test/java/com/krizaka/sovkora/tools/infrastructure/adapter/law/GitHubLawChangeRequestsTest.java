package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawChange;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubLawChangeRequestsTest {

  private static final String API = "https://api.github.com";
  private static final String REPO = API + "/repos/krizaka/sovkora-packs";

  @Test
  void a_change_becomes_a_branch_a_review_file_and_one_pull_request() {
    RestClient.Builder builder = RestClient.builder().baseUrl(API);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(REPO + "/git/ref/heads/main"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"object\":{\"sha\":\"base-sha\"}}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(REPO + "/git/refs"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("refs/heads/law-watch/")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("base-sha")))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(REPO + "/contents/law-watch/2026-10-23-changed-CCQ-1991-1942-fr.md"))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(REPO + "/pulls"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"base\":\"main\"")))
        .andRespond(
            withSuccess(
                "{\"html_url\":\"https://github.com/krizaka/sovkora-packs/pull/42\"}",
                MediaType.APPLICATION_JSON));
    GitHubLawChangeRequests requests =
        new GitHubLawChangeRequests(builder.build(), "krizaka", "sovkora-packs", "main");

    String url = requests.open(changed());

    server.verify();
    assertThat(url).isEqualTo("https://github.com/krizaka/sovkora-packs/pull/42");
  }

  private static LawChange changed() {
    LocalDate day = LocalDate.of(2026, 10, 23);
    LawArticle before =
        new LawArticle(
            "QC",
            "CCQ-1991",
            "1942",
            "fr",
            LocalDate.of(1999, 4, 1),
            "1942 au moins trois mois",
            "https://www.legisquebec.gouv.qc.ca/fr/version/lc/CCQ-1991?code=se:1942",
            "https://donnees.test/ccq.xml");
    LawArticle now =
        new LawArticle(
            "QC",
            "CCQ-1991",
            "1942",
            "fr",
            LocalDate.of(2026, 1, 1),
            "1942 au moins quatre mois",
            before.officialUrl(),
            "https://donnees.test/ccq.xml");
    return new LawChange(LawChange.Kind.CHANGED, before, now, day);
  }
}
