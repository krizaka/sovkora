package com.krizaka.sovkora.engine.core.infrastructure.config;

/**
 * Security governance configuration properties. Maps to {@code orazaka.security} in {@code
 * application.yml}.
 *
 * <p>The {@code disableAi} property acts as a hard governance kill-switch. When set to {@code
 * true}, the {@code DynamicPipelineExecutor} will throw a {@link SecurityException} if any
 * interceptor returning {@code isAiDependent() == true} is invoked.
 *
 * @param disableAi When {@code true}, blocks all AI-dependent interceptors with a hard {@link
 *     SecurityException}. This is a compliance shield, not a performance failover.
 *     <p><strong>Note</strong>: this is the ONLY constructor — deliberately. A no-argument
 *     constructor alongside this one caused Spring's constructor binding to choose the no-arg form
 *     and never read the property (ADR-055 §8). The default (off) is the boolean default.
 */
public record SecurityProperties(boolean disableAi) {}
