package com.krizaka.sovkora.automationservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A schedule trigger whose slot has come.
 *
 * @param triggerId the trigger
 * @param automationId its automation
 * @param schedule the slot rule
 * @param catchUp the missed-slot policy
 * @param dueAt the slot that came
 */
public record DueSchedule(
    UUID triggerId, UUID automationId, ScheduleSpec schedule, CatchUp catchUp, Instant dueAt) {}
