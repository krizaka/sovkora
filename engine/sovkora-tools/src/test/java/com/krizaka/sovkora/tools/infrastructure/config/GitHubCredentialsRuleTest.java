package com.krizaka.sovkora.tools.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The rule behind sovkora-engine#77: a GitHub App installation token lives an hour, so a properties
 * record never takes a GitHub token — it takes the App's id, installation and private key, and the
 * token is minted at run time ({@code GitHubAppInstallationTokens}). A new GitHub integration that
 * adds a {@code token} field fails here instead of an hour after its first start.
 */
class GitHubCredentialsRuleTest {

  /** Every properties record of this module that talks to GitHub. */
  private static final List<Class<?>> GITHUB_PROPERTIES =
      List.of(LawWatchProperties.class, RulesetPromotionProperties.class);

  @Test
  void no_github_properties_record_holds_a_static_token() {
    List<String> offenders = new ArrayList<>();
    for (Class<?> type : GITHUB_PROPERTIES) {
      collect(type, offenders);
    }
    assertThat(offenders).as("GitHub credentials are an App id and key, never a token").isEmpty();
  }

  @Test
  void every_github_properties_record_names_the_app() {
    for (Class<?> type : GITHUB_PROPERTIES) {
      List<String> names = new ArrayList<>();
      names(type, names);
      assertThat(names).as(type.getSimpleName()).contains("appId", "installationId", "privateKey");
    }
  }

  private static void collect(Class<?> type, List<String> offenders) {
    for (RecordComponent component : type.getRecordComponents()) {
      if (component.getName().toLowerCase(Locale.ROOT).contains("token")) {
        offenders.add(type.getSimpleName() + "." + component.getName());
      }
      if (component.getType().isRecord()) {
        collect(component.getType(), offenders);
      }
    }
  }

  private static void names(Class<?> type, List<String> names) {
    for (RecordComponent component : type.getRecordComponents()) {
      names.add(component.getName());
      if (component.getType().isRecord()) {
        names(component.getType(), names);
      }
    }
  }
}
