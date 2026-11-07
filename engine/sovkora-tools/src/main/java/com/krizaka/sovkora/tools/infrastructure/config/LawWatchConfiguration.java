package com.krizaka.sovkora.tools.infrastructure.config;

import com.krizaka.sovkora.tools.application.port.law.LawChangeRequests;
import com.krizaka.sovkora.tools.application.port.law.LawSettings;
import com.krizaka.sovkora.tools.application.port.law.LawSource;
import com.krizaka.sovkora.tools.application.service.LawImportService;
import com.krizaka.sovkora.tools.application.service.LawWatchService;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.GitHubAppInstallationTokens;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.GitHubLawChangeRequests;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the law watcher on top of {@link LawCorpusConfiguration}. Off by default; {@code
 * orazaka.law.watcher.enabled=true} starts the schedule, and then the corpus store (the job-service
 * law tables) must exist, as {@link LawCorpusConfiguration} needs it. What the watcher imports is
 * read from the {@code law.import.instruments} setting at each run, and it does nothing while
 * {@code law.corpus.enabled} is false.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(LawWatchProperties.class)
@ConditionalOnProperty(prefix = "orazaka.law.watcher", name = "enabled", havingValue = "true")
public class LawWatchConfiguration {

  @Bean
  LawChangeRequests lawChangeRequests(LawWatchProperties properties) {
    LawWatchProperties.GitHub github = properties.github();
    // The App's installation tokens, minted at run time and renewed before their hour ends: never
    // a static token, which would stop the watcher an hour after it starts (sovkora-engine#77).
    GitHubAppInstallationTokens tokens =
        GitHubAppInstallationTokens.forApp(
            github.apiUrl(),
            LawCorpusConfiguration.USER_AGENT,
            github.appId(),
            github.installationId(),
            github.privateKey());
    return new GitHubLawChangeRequests(
        tokens.client(), github.owner(), github.repository(), github.baseBranch());
  }

  @Bean
  LawWatchService lawWatchService(
      List<LawSource> sources, LawSettings settings, LawImportService importer) {
    return new LawWatchService(sources, settings, importer);
  }

  @Bean
  LawWatchJob lawWatchJob(LawWatchService service) {
    return new LawWatchJob(service);
  }
}
