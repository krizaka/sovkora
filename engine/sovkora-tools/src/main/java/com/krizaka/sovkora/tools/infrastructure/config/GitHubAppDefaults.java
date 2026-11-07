package com.krizaka.sovkora.tools.infrastructure.config;

/**
 * The defaults and the printable form shared by the properties of every GitHub App integration of
 * the law (the watcher, promotion): the rulesets repository, and a description that never holds the
 * private key.
 */
final class GitHubAppDefaults {

  private GitHubAppDefaults() {}

  static String owner(String value) {
    return orElse(value, "krizaka");
  }

  static String repository(String value) {
    return orElse(value, "sovkora-packs");
  }

  static String baseBranch(String value) {
    return orElse(value, "main");
  }

  static String apiUrl(String value) {
    return orElse(value, "https://api.github.com");
  }

  /**
   * What an App's properties print: the App and the repository, never the key.
   *
   * @return the description
   */
  static String describe(
      String name, Object flag, String appId, Long installationId, String repository) {
    return name
        + "["
        + (flag == null ? "" : "enabled=" + flag + ", ")
        + "appId="
        + appId
        + ", installationId="
        + installationId
        + ", repository="
        + repository
        + "]";
  }

  private static String orElse(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
