package com.krizaka.sovkora.knowledgeservice.analytics.domain.model;

/**
 * What a batch of analytics events did.
 *
 * @param accepted new events
 * @param duplicates events already known (same id) — at-least-once delivery is expected
 */
public record IngestResult(int accepted, int duplicates) {}
