package com.krizaka.sovkora.tools.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.application.port.law.LawChangeRequests;
import com.krizaka.sovkora.tools.application.port.law.LawCorpusStore;
import com.krizaka.sovkora.tools.application.service.LawImportService;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LawWatchConfigurationTest {

  /** A throwaway App key (PKCS#8 PEM), generated for the test: no key is ever committed. */
  private static final String KEY = pem();

  private static final String[] APP = {
    "orazaka.law.watcher.enabled=true",
    "orazaka.law.watcher.github.app-id=12345",
    "orazaka.law.watcher.github.installation-id=678",
    "orazaka.law.watcher.github.private-key=" + KEY
  };

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(LawCorpusConfiguration.class, LawWatchConfiguration.class)
          .withBean(RuntimeConfigProvider.class, LawCorpusConfigurationTest.OffConfig::new);

  @Test
  void the_watcher_is_off_by_default() {
    runner
        .withBean(LawCorpusStore.class, LawCorpusConfigurationTest.EmptyStore::new)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(LawWatchJob.class);
              assertThat(context).doesNotHaveBean(LawChangeRequests.class);
            });
  }

  @Test
  void enabled_it_wires_the_job_with_its_store_and_the_app() {
    runner
        .withBean(LawCorpusStore.class, LawCorpusConfigurationTest.EmptyStore::new)
        .withPropertyValues(APP)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(LawWatchJob.class);
              assertThat(context).hasSingleBean(LawChangeRequests.class);
              assertThat(context.getBean(LawWatchProperties.class).cron())
                  .isEqualTo("0 0 6 * * MON");
              assertThat(context).hasSingleBean(LawImportService.class);
              assertThat(context.getBean(LawWatchProperties.class).github().toString())
                  .doesNotContain("PRIVATE KEY")
                  .doesNotContain(KEY.substring(40, 80));
            });
  }

  @Test
  void a_static_token_is_not_a_credential_the_watcher_starts_with() {
    // An installation token lives an hour: configured statically, it would stop the watcher an
    // hour after it starts (sovkora-engine#77). Without the App's id and key, it won't start.
    runner
        .withBean(LawCorpusStore.class, LawCorpusConfigurationTest.EmptyStore::new)
        .withPropertyValues(
            "orazaka.law.watcher.enabled=true", "orazaka.law.watcher.github.token=test-token")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void enabled_without_an_installation_refuses_to_start() {
    runner
        .withBean(LawCorpusStore.class, LawCorpusConfigurationTest.EmptyStore::new)
        .withPropertyValues(
            "orazaka.law.watcher.enabled=true",
            "orazaka.law.watcher.github.app-id=12345",
            "orazaka.law.watcher.github.private-key=" + KEY)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void enabled_without_the_app_refuses_to_start() {
    runner
        .withBean(LawCorpusStore.class, LawCorpusConfigurationTest.EmptyStore::new)
        .withPropertyValues("orazaka.law.watcher.enabled=true")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void enabled_without_a_corpus_store_refuses_to_start() {
    runner.withPropertyValues(APP).run(context -> assertThat(context).hasFailed());
  }

  private static String pem() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      String body =
          Base64.getMimeEncoder(64, "\n".getBytes())
              .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
      return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
