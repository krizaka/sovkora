package com.krizaka.sovkora.engine.interceptors.governance;

import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.billing.domain.port.EntitlementProvider;
import com.krizaka.billing.domain.port.UnmeteredTurnRepository;
import com.krizaka.sovkora.engine.interceptors.governance.persistence.InterceptorPolicyRepository;
import com.krizaka.sovkora.engine.interceptors.governance.persistence.InterceptorPolicyStore;
import com.krizaka.sovkora.engine.interceptors.governance.persistence.ProviderPriceRepository;
import com.krizaka.sovkora.engine.interceptors.governance.persistence.ProviderSpendRepository;
import com.krizaka.sovkora.engine.interceptors.governance.persistence.SpendCapRepository;
import com.krizaka.sovkora.engine.interceptors.governance.persistence.SpendLedgerStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AutoConfiguration for the interceptor governance subsystem. Registers the policy persistence
 * facade ({@link InterceptorPolicyStore}) and the {@link PolicyConfigCache} via Spring SPI, plus
 * the billing gate.
 */
@AutoConfiguration
class GovernanceAutoConfiguration {

  /** Policy persistence, wired only where a repository exists to back it. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnBean(InterceptorPolicyRepository.class)
  static class PolicyConfiguration {

    @Bean
    InterceptorPolicyStore interceptorPolicyStore(InterceptorPolicyRepository repository) {
      return new InterceptorPolicyStore(repository);
    }

    @Bean
    PolicyConfigCache policyConfigCache(InterceptorPolicyStore store) {
      return new PolicyConfigCache(store);
    }
  }

  /**
   * The billing gate, wired wherever the billing ports are present.
   *
   * <p>Conditional on the beans rather than on {@code krizaka.billing.enabled}: with billing off
   * the ports resolve to their Null Objects and the interceptor is a pass-through costing two
   * method calls. That keeps the pipeline's shape identical either way, so turning billing on
   * cannot change which interceptors run — only what they answer.
   *
   * @param entitlementProvider reads what the actor's plan permits
   * @param creditAuthorizationClient reserves the turn's estimated cost
   * @return the gate
   */
  @Bean
  @ConditionalOnBean({EntitlementProvider.class, CreditAuthorizationClient.class})
  EntitlementInterceptor entitlementInterceptor(
      EntitlementProvider entitlementProvider,
      CreditAuthorizationClient creditAuthorizationClient,
      UnmeteredTurnRepository unmeteredTurns) {
    // Deliberately NOT part of the condition: a host that wires billing and no record of unmetered
    // turns fails to start, instead of silently losing the gate (ADR-064).
    return new EntitlementInterceptor(
        entitlementProvider, creditAuthorizationClient, unmeteredTurns);
  }

  /**
   * The scope guard, wired unconditionally.
   *
   * <p>No {@code @ConditionalOnBean} and no property: it needs nothing but the declaration in the
   * message, and a compliance control that is present only when some other bean happens to exist is
   * a control with a deployment shape nobody audited. A turn that declares no domain passes through
   * it at the cost of one map lookup (ADR-051).
   *
   * @return the guard
   */
  @Bean
  ScopeGuardInterceptor scopeGuardInterceptor() {
    return new ScopeGuardInterceptor();
  }

  /**
   * The other half of the gate: what the interceptor reserves, this closes.
   *
   * <p>Wired by the same condition so the pair cannot be deployed apart — a hold taken with no
   * listener to settle it would freeze the user's balance until the sweeper expired it.
   *
   * @param creditAuthorizationClient settles the turn against measured tokens
   * @return the settlement listener
   */
  @Bean
  @ConditionalOnBean(CreditAuthorizationClient.class)
  ChatSettlementListener chatSettlementListener(
      CreditAuthorizationClient creditAuthorizationClient) {
    return new ChatSettlementListener(creditAuthorizationClient);
  }

  /**
   * The monthly provider-spend cap: the rows' store, the control that applies them, the one writer
   * of the spend and the meters. Wired wherever the repositories exist, so a host with a database
   * cannot serve a chat turn without the cap.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnBean({
    SpendCapRepository.class,
    ProviderPriceRepository.class,
    ProviderSpendRepository.class
  })
  @EnableConfigurationProperties(SpendCapProperties.class)
  static class SpendCapConfiguration {

    @Bean
    SpendLedgerStore spendLedgerStore(
        SpendCapRepository caps, ProviderPriceRepository prices, ProviderSpendRepository spend) {
      return new SpendLedgerStore(caps, prices, spend);
    }

    @Bean
    SpendMetrics spendMetrics(ObjectProvider<MeterRegistry> registry) {
      return new SpendMetrics(registry.getIfAvailable(SimpleMeterRegistry::new));
    }

    @Bean
    SpendCapInterceptor spendCapInterceptor(
        SpendLedgerStore ledger, SpendCapProperties properties, SpendMetrics metrics) {
      return new SpendCapInterceptor(ledger, properties, metrics, Clock.systemUTC());
    }

    @Bean
    ProviderSpendListener providerSpendListener(
        SpendLedgerStore ledger, SpendCapProperties properties, SpendMetrics metrics) {
      return new ProviderSpendListener(ledger, properties, metrics, Clock.systemUTC());
    }
  }
}
