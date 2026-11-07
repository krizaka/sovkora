package com.krizaka.sovkora.interceptor.validation;

import com.krizaka.sovkora.core.domain.ports.outbound.CodeSandboxPort;
import com.krizaka.sovkora.core.domain.ports.outbound.ControlVerdictRecorder;
import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import com.krizaka.sovkora.core.domain.ports.outbound.TestShaperPort;
import com.krizaka.sovkora.core.domain.ports.outbound.ValidationPipelineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the validation interceptor module.
 *
 * <p>Registers the {@link CostShieldInterceptor}, the {@link ModelTestShaperAdapter} (Tier D), and
 * the {@link ClosedLoopValidationInterceptor}. The advisor is conditionally activated via {@code
 * orazaka.interceptor.validation.closed-loop.enabled}.
 *
 * @since 1.0.0
 */
@AutoConfiguration(
    afterName = "com.krizaka.sovkora.adapter.springai.SpringAiAdapterAutoConfiguration")
@EnableConfigurationProperties(ClosedLoopValidationProperties.class)
public class ValidationAutoConfiguration {

  /**
   * Registers the {@link CostShieldInterceptor} as a Spring bean.
   *
   * @return The cost shield interceptor instance.
   */
  /**
   * The crisis guard, registered unconditionally.
   *
   * <p>No {@code @ConditionalOnProperty} and no toggle: a safety control that a property can remove
   * is one that will be removed, in the deployment that most needs it. It is inert for a pack that
   * declares no crisis terms, which is every pack below {@code REGULATED} (ADR-055 §4).
   *
   * @return the interceptor
   */
  @Bean
  public SafetyInterceptor safetyInterceptor() {
    return new SafetyInterceptor();
  }

  /**
   * The verifier stage of grounded research, where a verifier is wired (sovkora-tools). Its
   * pipeline row ships disabled (ADR-087 slice 2).
   *
   * @param verifier the deterministic citation verifier
   * @param config the runtime settings
   * @return the stage
   */
  @Bean
  @ConditionalOnBean({
    com.krizaka.sovkora.core.domain.ports.inbound.CitationVerifier.class,
    com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider.class,
    com.krizaka.sovkora.core.domain.ports.outbound.PipelineConfigProvider.class
  })
  public CitationVerifierInterceptor citationVerifierInterceptor(
      com.krizaka.sovkora.core.domain.ports.inbound.CitationVerifier verifier,
      com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider config,
      com.krizaka.sovkora.core.domain.ports.outbound.PipelineConfigProvider pipelineRows) {
    return new CitationVerifierInterceptor(verifier, config, pipelineRows);
  }

  @Bean
  public CostShieldInterceptor costShieldInterceptor() {
    return new CostShieldInterceptor();
  }

  /**
   * Registers the {@link ModelTestShaperAdapter} bean for Tier D (Test-Driven Response).
   *
   * <p>Activated only when a {@link ProviderChatClient} is available in the context. Uses its first
   * registered model.
   *
   * @param chatClient The port that asks a model.
   * @return The test shaper adapter.
   */
  @Bean
  @ConditionalOnBean(ProviderChatClient.class)
  @ConditionalOnProperty(
      prefix = "orazaka.interceptor.validation.closed-loop",
      name = "tdr-enabled",
      havingValue = "true",
      matchIfMissing = false)
  public ModelTestShaperAdapter modelTestShaperAdapter(ProviderChatClient chatClient) {
    if (chatClient.providers().isEmpty()) {
      throw new IllegalStateException(
          "Tier D (TDR) enabled but no ChatModel available in context.");
    }
    return new ModelTestShaperAdapter(chatClient);
  }

  /**
   * Registers the {@link ClosedLoopValidationInterceptor} bean.
   *
   * <p>Activated only when {@code orazaka.interceptor.validation.closed-loop.enabled=true}. Tier C
   * (Semantic Debate) and Tier D (TDR) gracefully degrade when their dependencies are absent.
   *
   * @param properties Closed-loop validation configuration.
   * @param chatClient The port that asks a model (no model registered: Tier C and adjustment
   *     degrade).
   * @param validationPipelineRepository Dynamic pipeline config repository (nullable).
   * @param testShaperPort Tier D assertion generator (nullable).
   * @param verdictRecorder Persists adjustments and escalations through the outbox (nullable).
   * @param sandbox Tier B code sandbox (nullable — the built-in structural crash-test still runs).
   * @return The quantum validation advisor.
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "orazaka.interceptor.validation.closed-loop",
      name = "enabled",
      havingValue = "true")
  public ClosedLoopValidationInterceptor closedLoopValidationInterceptor(
      ClosedLoopValidationProperties properties,
      ProviderChatClient chatClient,
      @Autowired(required = false) ValidationPipelineRepository validationPipelineRepository,
      @Autowired(required = false) TestShaperPort testShaperPort,
      @Autowired(required = false) ControlVerdictRecorder verdictRecorder,
      @Autowired(required = false) CodeSandboxPort sandbox) {
    ProviderChatClient debateModel = chatClient.providers().isEmpty() ? null : chatClient;
    return new ClosedLoopValidationInterceptor(
        properties,
        debateModel,
        validationPipelineRepository,
        testShaperPort,
        verdictRecorder,
        sandbox,
        java.time.Clock.systemUTC());
  }
}
