package com.krizaka.sovkora.jobs.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobExecutionContextTest {

  @Test
  void carriesTheActorAndTheirPreferences() {
    JobExecutionContext context =
        new JobExecutionContext(
            "actor-1", "corr-1", Map.of("tone", "premium"), Set.of("ROLE_USER"));

    assertEquals("actor-1", context.actorId());
    assertEquals("corr-1", context.correlationId());
    assertEquals("premium", context.preferences().get("tone"));
    assertTrue(context.hasAuthority("ROLE_USER"));
    assertFalse(context.hasAuthority("ROLE_ADMIN"));
  }

  @Test
  @DisplayName("Defensively copied — a producer cannot mutate a context after handing it over")
  void isDefensivelyCopied() {
    Map<String, Object> prefs = new HashMap<>();
    prefs.put("tone", "premium");
    Set<String> roles = new HashSet<>(Set.of("ROLE_USER"));
    JobExecutionContext context = new JobExecutionContext("a", "c", prefs, roles);

    prefs.put("tone", "cheap");
    roles.add("ROLE_ADMIN");

    assertEquals("premium", context.preferences().get("tone"));
    assertFalse(context.hasAuthority("ROLE_ADMIN"));
    assertThrows(UnsupportedOperationException.class, () -> context.preferences().put("x", "y"));
  }

  @Test
  void nullsCollapseToEmpty_soAnExecutorNeverGuardsAgainstThem() {
    JobExecutionContext context = new JobExecutionContext("a", "c", null, null);

    assertTrue(context.preferences().isEmpty());
    assertTrue(context.authorities().isEmpty());
  }

  @Test
  void rejects_anExecutionWithNoActorOrCorrelation() {
    assertThrows(
        NullPointerException.class, () -> new JobExecutionContext(null, "c", Map.of(), Set.of()));
    assertThrows(
        NullPointerException.class, () -> new JobExecutionContext("a", null, Map.of(), Set.of()));
  }
}
