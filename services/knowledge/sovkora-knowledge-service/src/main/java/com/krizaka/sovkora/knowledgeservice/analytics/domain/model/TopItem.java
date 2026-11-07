package com.krizaka.sovkora.knowledgeservice.analytics.domain.model;

/**
 * An item among the most engaging of the range — only when at least the threshold of subjects
 * engaged it.
 *
 * @param itemId the item
 * @param ownerId its owner, when known
 * @param subjects distinct subjects who engaged it positively
 * @param engagements positive events
 * @param views views
 * @param meanWatchedPct the mean share watched of the views that reported it
 */
public record TopItem(
    String itemId,
    String ownerId,
    long subjects,
    long engagements,
    long views,
    Double meanWatchedPct) {}
