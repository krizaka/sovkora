package com.krizaka.sovkora.automationservice.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The sweep that fires schedules and starts waiting runs ({@code
 * orazaka.automation-service.scheduler}): wiring only — when and how often, not what is allowed.
 *
 * @param enabled whether this instance sweeps
 * @param interval the pause between two sweeps
 */
@ConfigurationProperties(prefix = "orazaka.automation-service.scheduler")
public record SchedulerProperties(
    @DefaultValue("true") boolean enabled, @DefaultValue("15s") Duration interval) {}
