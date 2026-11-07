package com.krizaka.sovkora.knowledge.analytics.domain.model;

/** What an event's value measures — the {@code analytics_value_unit} enum of the database. */
public enum ValueUnit {
  NONE,
  PERCENT,
  SECONDS,
  CENTS,
  COUNT
}
