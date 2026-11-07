package com.krizaka.sovkora.knowledgeservice.analytics.domain.model;

/**
 * Which endpoint receives a kind — the {@code analytics_kind_source} enum of the database. A
 * product sends each event once: a {@code RECOMMENDER} kind goes to the recommender, which
 * analytics reads back through the {@code analytics_event_all} view (ADR-081 §2).
 */
public enum KindSource {
  ANALYTICS,
  RECOMMENDER
}
