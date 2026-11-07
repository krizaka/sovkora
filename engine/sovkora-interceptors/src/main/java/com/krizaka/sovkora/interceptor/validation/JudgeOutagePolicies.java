package com.krizaka.sovkora.interceptor.validation;

import com.krizaka.sovkora.core.domain.model.ValidationPipelineConfiguration;
import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import com.krizaka.sovkora.core.domain.model.control.JudgeOutagePolicy;
import com.krizaka.sovkora.core.domain.ports.outbound.ValidationPipelineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads what the control loop does when a judge tier is unavailable: the admin's value on the
 * step's payload ({@value JudgeOutagePolicy#PAYLOAD_KEY}), else the static fallback of the
 * properties (escalate by default). Admin data, never a silent pass (sovkora-engine#125).
 */
final class JudgeOutagePolicies {

  private static final Logger logger = LoggerFactory.getLogger(JudgeOutagePolicies.class);

  private final ClosedLoopValidationProperties properties;
  private final ValidationPipelineRepository repository;

  JudgeOutagePolicies(
      ClosedLoopValidationProperties properties, ValidationPipelineRepository repository) {
    this.properties = properties;
    this.repository = repository;
  }

  JudgeOutagePolicy of(ValidationStepType step) {
    JudgeOutagePolicy fallback = properties.onJudgeOutage();
    if (repository == null) {
      return fallback;
    }
    try {
      return repository.findAllOrderedByExecution().stream()
          .filter(config -> config.stepType() == step)
          .findFirst()
          .map(ValidationPipelineConfiguration::configurationPayload)
          .map(
              payload ->
                  JudgeOutagePolicy.parse(payload.get(JudgeOutagePolicy.PAYLOAD_KEY), fallback))
          .orElse(fallback);
    } catch (RuntimeException e) {
      logger.warn("Failed to read the judge outage policy — using the static one.", e);
      return fallback;
    }
  }
}
