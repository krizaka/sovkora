package com.krizaka.sovkora.knowledgeservice.analytics.domain.model;

/**
 * One step of a funnel.
 *
 * @param kind the kind a subject does to reach it
 * @param suppressed true when fewer subjects than the threshold reached it
 * @param subjects subjects who reached it, absent when suppressed
 * @param fromPrevious the share of the previous step's subjects, absent for the first or when
 *     suppressed
 * @param fromFirst the share of the first step's subjects, absent when suppressed
 */
public record FunnelStep(
    String kind, boolean suppressed, Long subjects, Double fromPrevious, Double fromFirst) {}
