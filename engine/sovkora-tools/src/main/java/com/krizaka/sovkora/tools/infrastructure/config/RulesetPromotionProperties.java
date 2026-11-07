package com.krizaka.sovkora.tools.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The rulesets repository a promotion opens its pull request in, and the GitHub App that opens it
 * (ADR-089 §5 step 4). Off unless {@code enabled}. There is no token here on purpose: the App mints
 * its installation tokens from its id and private key, and a personal token is never accepted.
 *
 * @param enabled whether promotion (and the pack reads it needs) is wired
 * @param appId the GitHub App id ({@code krizaka-agents})
 * @param installationId the App's installation on the organisation
 * @param privateKey the App's private key in PEM, from the deployment's secrets
 * @param owner the owner, {@code krizaka} by default
 * @param repository the repository, {@code sovkora-packs} by default
 * @param baseBranch the branch requests are opened against, {@code main} by default
 * @param apiUrl the GitHub API, {@code https://api.github.com} by default
 */
@ConfigurationProperties(prefix = "orazaka.law.promotion")
public record RulesetPromotionProperties(
    boolean enabled,
    String appId,
    Long installationId,
    String privateKey,
    String owner,
    String repository,
    String baseBranch,
    String apiUrl) {

  /** Fills the defaults. */
  public RulesetPromotionProperties {
    owner = GitHubAppDefaults.owner(owner);
    repository = GitHubAppDefaults.repository(repository);
    baseBranch = GitHubAppDefaults.baseBranch(baseBranch);
    apiUrl = GitHubAppDefaults.apiUrl(apiUrl);
  }

  @Override
  public String toString() {
    // The private key is never printed.
    return GitHubAppDefaults.describe(
        "RulesetPromotionProperties",
        enabled,
        appId,
        installationId,
        owner + "/" + repository + "@" + baseBranch);
  }
}
