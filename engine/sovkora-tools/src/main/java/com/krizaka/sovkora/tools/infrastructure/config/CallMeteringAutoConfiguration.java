package com.krizaka.sovkora.tools.infrastructure.config;

import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckMeter;
import com.krizaka.sovkora.tools.application.port.law.ChatTokenMeter;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import com.krizaka.sovkora.tools.infrastructure.adapter.billing.CreditHoldChatTokenMeter;
import com.krizaka.sovkora.tools.infrastructure.adapter.billing.CreditHoldCheckMeter;
import com.krizaka.sovkora.tools.infrastructure.adapter.billing.CreditHoldResearchMeter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * The {@code CALL} meters of an external check and of a grounded research, over the credit hold
 * (ADR-033, krizaka/sovkora-packs#138).
 *
 * <p>Wired where a {@link CreditAuthorizationClient} exists, after the billing client's own
 * auto-configuration so the condition sees it. A host without one gets no meter, and then the
 * consumers' fail-closed fallbacks apply: the law retrieval refuses every grounded call ({@code
 * LawRetrievalConfiguration}), and no external check can be assembled without a meter. A host's own
 * meter wins.
 */
@AutoConfiguration(afterName = "com.krizaka.billing.client.BillingClientAutoConfiguration")
@ConditionalOnBean(CreditAuthorizationClient.class)
public class CallMeteringAutoConfiguration {

  /**
   * The meter of an external check.
   *
   * @param billing the credit hold port
   * @return the meter
   */
  @Bean
  @ConditionalOnMissingBean(CheckMeter.class)
  public CheckMeter creditHoldCheckMeter(CreditAuthorizationClient billing) {
    return new CreditHoldCheckMeter(billing);
  }

  /**
   * The meter of a grounded research call.
   *
   * @param billing the credit hold port
   * @return the meter
   */
  @Bean
  @ConditionalOnMissingBean(ResearchMeter.class)
  public ResearchMeter creditHoldResearchMeter(CreditAuthorizationClient billing) {
    return new CreditHoldResearchMeter(billing);
  }

  /**
   * The meter of the chat tokens of a grounded research call.
   *
   * @param billing the credit hold port
   * @return the meter
   */
  @Bean
  @ConditionalOnMissingBean(ChatTokenMeter.class)
  public ChatTokenMeter creditHoldChatTokenMeter(CreditAuthorizationClient billing) {
    return new CreditHoldChatTokenMeter(billing);
  }
}
