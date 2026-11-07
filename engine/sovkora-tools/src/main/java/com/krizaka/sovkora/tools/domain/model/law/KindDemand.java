package com.krizaka.sovkora.tools.domain.model.law;

import java.time.Instant;

/**
 * One row of {@code kind_demand} (ADR-089 §6): how often a kind with no promoted ruleset, or an
 * unknown one, was asked for. Never a document, never its content.
 *
 * @param jurisdiction the jurisdiction
 * @param kindId the kind, or {@code UNKNOWN}
 * @param count how many runs asked for it
 * @param firstSeen the first
 * @param lastSeen the last
 */
public record KindDemand(
    String jurisdiction, String kindId, long count, Instant firstSeen, Instant lastSeen) {}
