package com.krizaka.sovkora.automationservice.domain.model;

import java.util.Map;

/**
 * A run with what started it.
 *
 * @param run the run
 * @param payload the webhook body it was started with, if it was a JSON object and was kept
 */
public record RunDetail(AutomationRun run, Map<String, Object> payload) {}
