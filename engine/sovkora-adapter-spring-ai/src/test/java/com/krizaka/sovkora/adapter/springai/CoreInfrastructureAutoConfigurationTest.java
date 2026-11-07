package com.krizaka.sovkora.adapter.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.sovkora.core.domain.ports.outbound.AudioTranscriber;
import com.krizaka.sovkora.core.domain.ports.outbound.InfrastructureStatusProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import com.krizaka.sovkora.core.infrastructure.config.SecurityProperties;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The core's plain configuration records are bound here, the core itself knows no Spring. */
class CoreInfrastructureAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CoreInfrastructureAutoConfiguration.class))
          .withBean(CatalogModelManager.class, () -> mock(CatalogModelManager.class))
          .withBean(AudioTranscriber.class, () -> mock(AudioTranscriber.class))
          .withBean(
              InfrastructureStatusProvider.class, () -> mock(InfrastructureStatusProvider.class))
          .withBean(ModelCatalogProvider.class, () -> mock(ModelCatalogProvider.class));

  @Test
  void theKillSwitchBindsFromAPropertySource() {
    runner
        .withPropertyValues("orazaka.security.disable-ai=true")
        .run(context -> assertThat(context.getBean(SecurityProperties.class).disableAi()).isTrue());
  }

  @Test
  void theKillSwitchIsOffUnlessTheDeploymentSaysSo() {
    runner.run(
        context -> assertThat(context.getBean(SecurityProperties.class).disableAi()).isFalse());
  }

  @Test
  void theOrchestrationSwitchIsReadFromTheYaml() {
    runner
        .withPropertyValues("orazaka.core.orchestration.enabled=false")
        .run(
            context -> {
              CoreProperties core = context.getBean(CoreProperties.class);
              assertThat(core.orchestration().enabled()).isFalse();
              assertThat(core.defaultProvider()).isEqualTo("ollama");
            });
  }

  @Test
  void theSynchronousRuntimeIsAvailable() {
    runner.run(
        context ->
            assertThat(context.getBean("synchronousExecutor", ExecutorService.class)).isNotNull());
  }
}
