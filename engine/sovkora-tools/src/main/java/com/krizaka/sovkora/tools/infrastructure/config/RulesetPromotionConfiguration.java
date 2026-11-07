package com.krizaka.sovkora.tools.infrastructure.config;

import com.krizaka.sovkora.tools.application.port.law.RulesetPack;
import com.krizaka.sovkora.tools.application.port.law.RulesetPromotionPublisher;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.GitHubAppInstallationTokens;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.GitHubRulesetPack;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.GitHubRulesetPromotionPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the rulesets repository on GitHub for stage 5 of ADR-089: the pack reader (kinds, golden
 * cases, ruleset revisions) and the promotion publisher, both authenticated as the GitHub App with
 * its installation tokens. Off by default ({@code orazaka.law.promotion.enabled=true}); without it,
 * {@link LawReviewConfiguration} answers promotion as off.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RulesetPromotionProperties.class)
@ConditionalOnProperty(prefix = "orazaka.law.promotion", name = "enabled", havingValue = "true")
public class RulesetPromotionConfiguration {

  @Bean
  GitHubAppInstallationTokens rulesetAppTokens(RulesetPromotionProperties properties) {
    return GitHubAppInstallationTokens.forApp(
        properties.apiUrl(),
        LawCorpusConfiguration.USER_AGENT,
        properties.appId(),
        properties.installationId(),
        properties.privateKey());
  }

  @Bean
  RulesetPack rulesetPack(
      RulesetPromotionProperties properties, GitHubAppInstallationTokens tokens) {
    return new GitHubRulesetPack(
        tokens.client(), properties.owner(), properties.repository(), properties.baseBranch());
  }

  @Bean
  RulesetPromotionPublisher rulesetPromotionPublisher(
      RulesetPromotionProperties properties, GitHubAppInstallationTokens tokens) {
    return new GitHubRulesetPromotionPublisher(
        tokens.client(), properties.owner(), properties.repository(), properties.baseBranch());
  }
}
