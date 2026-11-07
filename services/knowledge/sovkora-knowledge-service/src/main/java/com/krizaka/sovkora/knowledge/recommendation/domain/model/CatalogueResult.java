package com.krizaka.sovkora.knowledge.recommendation.domain.model;

/**
 * What a batch of items did.
 *
 * @param upserted items written
 * @param embedded items whose text changed and were embedded
 * @param model the embedding model, when any item was embedded
 * @param runId the embedding run, when any item was embedded
 */
public record CatalogueResult(int upserted, int embedded, String model, String runId) {}
