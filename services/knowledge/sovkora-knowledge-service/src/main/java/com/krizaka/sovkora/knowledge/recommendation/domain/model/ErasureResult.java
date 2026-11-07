package com.krizaka.sovkora.knowledge.recommendation.domain.model;

/**
 * What an erasure removed.
 *
 * @param events events of the forgotten people
 * @param items items forgotten
 */
public record ErasureResult(int events, int items) {}
