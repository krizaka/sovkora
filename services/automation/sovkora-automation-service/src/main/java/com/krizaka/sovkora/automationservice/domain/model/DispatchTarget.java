package com.krizaka.sovkora.automationservice.domain.model;

import java.util.Map;
import java.util.UUID;

/**
 * What it takes to start the Studio run of a recorded run: whose it is and what to run.
 *
 * @param runId the recorded run
 * @param ownerId the automation's owner, on whose behalf the Studio run starts
 * @param studioKey the Studio
 * @param inputs the automation's fixed inputs
 * @param status the run's current status
 */
public record DispatchTarget(
    UUID runId, String ownerId, String studioKey, Map<String, Object> inputs, RunStatus status) {}
