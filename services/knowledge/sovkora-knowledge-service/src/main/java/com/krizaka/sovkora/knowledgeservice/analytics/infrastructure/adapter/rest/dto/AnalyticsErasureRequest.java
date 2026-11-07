package com.krizaka.sovkora.knowledgeservice.analytics.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Identifiers;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Subjects and items to forget (at most {@value #MAX} of each).
 *
 * @param subjects pseudonyms
 * @param items item ids
 */
public record AnalyticsErasureRequest(List<String> subjects, List<String> items) {

  /** The most ids of each kind one request may name. */
  public static final int MAX = 500;

  /** The validated pseudonyms. */
  public Set<String> subjectIds() {
    Set<String> out = new LinkedHashSet<>();
    for (String s : bounded(subjects, "subjects")) {
      out.add(Identifiers.requirePseudonym(s));
    }
    return out;
  }

  /** The validated item ids. */
  public Set<String> itemIds() {
    Set<String> out = new LinkedHashSet<>();
    for (String s : bounded(items, "items")) {
      out.add(Identifiers.require(s, "items"));
    }
    return out;
  }

  private static List<String> bounded(List<String> ids, String field) {
    if (ids == null) {
      return List.of();
    }
    if (ids.size() > MAX) {
      throw new IllegalArgumentException(field + ": at most " + MAX);
    }
    return ids;
  }
}
