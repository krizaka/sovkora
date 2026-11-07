package com.krizaka.sovkora.automationservice.infrastructure.adapter.schedule;

import com.krizaka.sovkora.automationservice.application.service.RunSweepService;
import com.krizaka.sovkora.automationservice.domain.port.CallLimiter;
import com.krizaka.sovkora.automationservice.infrastructure.config.SchedulerProperties;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The clock of the automation service: every interval it runs one sweep (schedule slots, queued
 * runs, lost events, retention). The pause between sweeps is wiring; what a sweep decides is the
 * rule set's. A failing sweep is logged and tried again at the next tick.
 */
@Component
class SweepTicker {

  private static final Logger logger = LoggerFactory.getLogger(SweepTicker.class);

  private final RunSweepService sweep;
  private final CallLimiter limiter;
  private final SchedulerProperties properties;

  SweepTicker(RunSweepService sweep, CallLimiter limiter, SchedulerProperties properties) {
    this.sweep = Objects.requireNonNull(sweep, "RunSweepService cannot be null");
    this.limiter = Objects.requireNonNull(limiter, "CallLimiter cannot be null");
    this.properties = Objects.requireNonNull(properties, "SchedulerProperties cannot be null");
  }

  @Scheduled(fixedDelayString = "${orazaka.automation-service.scheduler.interval:15s}")
  void tick() {
    if (!properties.enabled()) {
      return;
    }
    try {
      sweep.sweep();
      limiter.evictIdle();
    } catch (RuntimeException e) {
      logger.warn("the automation sweep failed: {}", e.getClass().getSimpleName());
    }
  }
}
