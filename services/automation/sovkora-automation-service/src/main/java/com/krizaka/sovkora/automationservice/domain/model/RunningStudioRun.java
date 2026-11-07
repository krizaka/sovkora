package com.krizaka.sovkora.automationservice.domain.model;

import java.util.UUID;

/**
 * A run waiting on its Studio run.
 *
 * @param runId the recorded run
 * @param studioRunId the Studio run
 * @param ownerId whose it is
 */
public record RunningStudioRun(UUID runId, UUID studioRunId, String ownerId) {}
