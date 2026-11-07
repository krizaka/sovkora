package com.krizaka.sovkora.tools.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckMeter;
import com.krizaka.sovkora.tools.application.port.law.ChatTokenMeter;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import com.krizaka.sovkora.tools.infrastructure.adapter.billing.CreditHoldChatTokenMeter;
import com.krizaka.sovkora.tools.infrastructure.adapter.billing.CreditHoldCheckMeter;
import com.krizaka.sovkora.tools.infrastructure.adapter.billing.CreditHoldResearchMeter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("the CALL meters are wired where a billing client exists, and only there")
class CallMeteringAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CallMeteringAutoConfiguration.class));

  @Test
  @DisplayName("a billing client gives both meters over the credit hold")
  void wired() {
    runner
        .withBean(CreditAuthorizationClient.class, () -> mock(CreditAuthorizationClient.class))
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx.getBean(CheckMeter.class)).isInstanceOf(CreditHoldCheckMeter.class);
              assertThat(ctx.getBean(ResearchMeter.class))
                  .isInstanceOf(CreditHoldResearchMeter.class);
              assertThat(ctx.getBean(ChatTokenMeter.class))
                  .isInstanceOf(CreditHoldChatTokenMeter.class);
            });
  }

  @Test
  @DisplayName("no billing client, no meter: the fail-closed fallbacks apply")
  void noBilling() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx).doesNotHaveBean(CheckMeter.class);
          assertThat(ctx).doesNotHaveBean(ResearchMeter.class);
          assertThat(ctx).doesNotHaveBean(ChatTokenMeter.class);
        });
  }

  @Test
  @DisplayName("a host's own meter is kept")
  void hostMeterKept() {
    CheckMeter own = (runId, actorId, provider, purpose) -> null;
    ResearchMeter ownResearch = (runId, actorId, providerKey) -> null;
    runner
        .withBean(CreditAuthorizationClient.class, () -> mock(CreditAuthorizationClient.class))
        .withBean(CheckMeter.class, () -> own)
        .withBean(ResearchMeter.class, () -> ownResearch)
        .run(
            ctx -> {
              assertThat(ctx.getBean(CheckMeter.class)).isSameAs(own);
              assertThat(ctx.getBean(ResearchMeter.class)).isSameAs(ownResearch);
            });
  }

  @Test
  @DisplayName("it is registered as an auto-configuration, after the billing client's")
  void registered() throws IOException {
    try (InputStream imports =
        getClass()
            .getResourceAsStream(
                "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
      assertThat(imports).isNotNull();
      assertThat(new String(imports.readAllBytes(), StandardCharsets.UTF_8))
          .contains(CallMeteringAutoConfiguration.class.getName());
    }
  }
}
