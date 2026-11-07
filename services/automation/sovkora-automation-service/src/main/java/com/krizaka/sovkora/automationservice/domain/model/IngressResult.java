package com.krizaka.sovkora.automationservice.domain.model;

import java.util.UUID;

/**
 * An accepted webhook call: the run it recorded.
 *
 * @param runId the run
 * @param status always {@link RunStatus#QUEUED} at acceptance
 */
public record IngressResult(UUID runId, RunStatus status) {}
