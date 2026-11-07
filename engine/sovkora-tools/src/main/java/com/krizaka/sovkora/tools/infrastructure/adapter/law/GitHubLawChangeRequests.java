package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import com.krizaka.sovkora.tools.application.port.law.LawChangeRequests;
import com.krizaka.sovkora.tools.domain.model.law.LawChange;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * Opens one review pull request per change in the rulesets repository: a branch from the base, one
 * review file, the request. Authenticated with the GitHub App token of the deployment; the token is
 * configuration, never code.
 */
public final class GitHubLawChangeRequests implements LawChangeRequests {

  private final RestClient github;
  private final String owner;
  private final String repository;
  private final String baseBranch;

  /**
   * Creates the adapter.
   *
   * @param github the GitHub API client, already authenticated
   * @param owner the owner of the rulesets repository
   * @param repository the rulesets repository
   * @param baseBranch the branch the requests are opened against
   */
  public GitHubLawChangeRequests(
      RestClient github, String owner, String repository, String baseBranch) {
    this.github = github;
    this.owner = owner;
    this.repository = repository;
    this.baseBranch = baseBranch;
  }

  @Override
  public String open(LawChange change) {
    LawChangeReview review = LawChangeReview.of(change);
    String repo = "/repos/" + owner + "/" + repository;
    Map<?, ?> base =
        github.get().uri(repo + "/git/ref/heads/" + baseBranch).retrieve().body(Map.class);
    String sha = (String) ((Map<?, ?>) base.get("object")).get("sha");
    github
        .post()
        .uri(repo + "/git/refs")
        .body(Map.of("ref", "refs/heads/" + review.branch(), "sha", sha))
        .retrieve()
        .toBodilessEntity();
    String content =
        Base64.getEncoder().encodeToString(review.content().getBytes(StandardCharsets.UTF_8));
    github
        .put()
        .uri(repo + "/contents/" + review.path())
        .body(
            Map.of(
                "message", review.title(),
                "content", content,
                "branch", review.branch()))
        .retrieve()
        .toBodilessEntity();
    Map<?, ?> pull =
        github
            .post()
            .uri(repo + "/pulls")
            .body(
                Map.of(
                    "title", review.title(),
                    "head", review.branch(),
                    "base", baseBranch,
                    "body", review.body()))
            .retrieve()
            .body(Map.class);
    return (String) pull.get("html_url");
  }
}
