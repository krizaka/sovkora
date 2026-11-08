package com.krizaka.sovkora.knowledge.analytics.domain.model;

/**
 * What an erasure removed, across the analytics and the recommender's events (ADR-081 §4).
 *
 * @param subjects pseudonyms forgotten
 * @param items items forgotten
 * @param events event rows deleted
 */
public record ErasureResult(int subjects, int items, int events) {}
