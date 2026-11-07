package com.krizaka.sovkora.tools.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the law watcher (ADR-089, krizaka/sovkora#400). Off unless {@code enabled} is set.
 *
 * @param enabled whether the scheduled watch runs
 * @param cron the schedule, Spring cron (sec min hour day month weekday); weekly by default
 * @param quebec the Données Québec snapshot
 * @param github the rulesets repository and the GitHub App that opens the review requests
 */
@ConfigurationProperties(prefix = "orazaka.law.watcher")
public record LawWatchProperties(boolean enabled, String cron, Quebec quebec, GitHub github) {

  public LawWatchProperties {
    cron = cron == null || cron.isBlank() ? "0 0 6 * * MON" : cron;
    quebec = quebec == null ? new Quebec(null) : quebec;
    github = github == null ? new GitHub(null, null, null, null, null, null, null) : github;
  }

  /**
   * The Données Québec snapshot.
   *
   * @param datasetUrl the URL of the statutes zip; the current snapshot by default
   */
  public record Quebec(String datasetUrl) {

    public Quebec {
      datasetUrl =
          datasetUrl == null || datasetUrl.isBlank()
              ? "https://www.donneesquebec.ca/recherche/dataset/c8433300-f752-4815-8ea2-69cad416dd80/resource/d94786e8-7b04-4ed8-a2ac-8a148b7969d3/download/20261021_lois.zip"
              : datasetUrl;
    }
  }

  /**
   * The rulesets repository and the GitHub App that opens the review requests. There is no token
   * here on purpose: an installation token lives an hour, so the App mints one at run time from its
   * id and private key (sovkora-engine#77), and a personal token is never accepted.
   *
   * @param appId the GitHub App id ({@code krizaka-agents}); required when enabled
   * @param installationId the App's installation on the organisation; required when enabled
   * @param privateKey the App's private key in PEM, from the deployment's secrets; required when
   *     enabled
   * @param owner the owner, {@code krizaka} by default
   * @param repository the repository, {@code sovkora-packs} by default
   * @param baseBranch the branch requests are opened against, {@code main} by default
   * @param apiUrl the GitHub API, {@code https://api.github.com} by default
   */
  public record GitHub(
      String appId,
      Long installationId,
      String privateKey,
      String owner,
      String repository,
      String baseBranch,
      String apiUrl) {

    public GitHub {
      owner = GitHubAppDefaults.owner(owner);
      repository = GitHubAppDefaults.repository(repository);
      baseBranch = GitHubAppDefaults.baseBranch(baseBranch);
      apiUrl = GitHubAppDefaults.apiUrl(apiUrl);
    }

    @Override
    public String toString() {
      // The private key is never printed.
      return GitHubAppDefaults.describe(
          "GitHub", null, appId, installationId, owner + "/" + repository + "@" + baseBranch);
    }
  }
}
