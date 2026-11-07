package com.krizaka.sovkora.knowledgeservice.analytics.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * An ordered funnel (ADR-081 §3): a subject reaches step <i>k</i> when it does kind <i>k</i> after
 * its step <i>k−1</i>, within the window.
 *
 * @param range the range the first step is counted in
 * @param owner the owner it is narrowed to, when one was asked
 * @param windowHours how long after a step the next one still counts
 * @param minAudience the smallest audience a step is reported for
 * @param steps the steps, in order
 */
public record Funnel(
    Range range, String owner, int windowHours, int minAudience, List<FunnelStep> steps) {

  /** The fewest and the most steps a funnel has. */
  public static final int MIN_STEPS = 2;

  public static final int MAX_STEPS = 6;

  public Funnel {
    steps = List.copyOf(steps);
  }

  /** The kinds of a {@code steps=a,b,c} parameter, or an {@link IllegalArgumentException}. */
  public static List<String> parseSteps(String csv) {
    if (csv == null || csv.isBlank()) {
      throw new IllegalArgumentException("steps is required");
    }
    List<String> kinds = new ArrayList<>();
    for (String part : csv.split(",")) {
      kinds.add(EventKind.requireName(part.trim()));
    }
    if (kinds.size() < MIN_STEPS || kinds.size() > MAX_STEPS) {
      throw new IllegalArgumentException(
          "steps: " + MIN_STEPS + " to " + MAX_STEPS + " kinds, comma-separated");
    }
    if (new LinkedHashSet<>(kinds).size() != kinds.size()) {
      throw new IllegalArgumentException("steps: a kind appears once");
    }
    return List.copyOf(kinds);
  }

  /** The funnel from the subjects counted at each step, with its conversions and suppressions. */
  public static Funnel of(
      Range range,
      String owner,
      int windowHours,
      int minAudience,
      List<String> kinds,
      List<Long> reached) {
    if (kinds.size() != reached.size()) {
      throw new IllegalArgumentException("one count per step");
    }
    List<FunnelStep> steps = new ArrayList<>();
    long first = reached.isEmpty() ? 0 : reached.getFirst();
    for (int i = 0; i < kinds.size(); i++) {
      long n = reached.get(i);
      if (n < minAudience) {
        steps.add(new FunnelStep(kinds.get(i), true, null, null, null));
        continue;
      }
      Double fromPrevious = i == 0 ? null : share(n, reached.get(i - 1));
      steps.add(new FunnelStep(kinds.get(i), false, n, fromPrevious, share(n, first)));
    }
    return new Funnel(range, owner, windowHours, minAudience, steps);
  }

  private static Double share(long part, long whole) {
    return whole == 0 ? null : Figure.round(part / (double) whole);
  }
}
