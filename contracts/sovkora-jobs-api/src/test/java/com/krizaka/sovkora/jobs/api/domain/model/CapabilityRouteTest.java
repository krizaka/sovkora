package com.krizaka.sovkora.jobs.api.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CapabilityRouteTest {

  @Test
  void carriesWhereACapabilityRunsAndWhatItBillsIn() {
    CapabilityRoute route =
        new CapabilityRoute(
            "a.capability", "job.video.generate", "OUTPUT_SECOND", "VIDEO", "BATCH", true);

    assertEquals("a.capability", route.featureKey());
    assertEquals("job.video.generate", route.routingKey());
    assertEquals("OUTPUT_SECOND", route.billableUnit());
    assertTrue(route.enabled());
  }

  @Test
  void acceptsAnAbsentUnit_becauseSomeUnitsAreAPropertyOfTheModelAndNotOfTheCapability() {
    assertNull(
        new CapabilityRoute("a.capability", "job.text.process", null, "CHAT", "BATCH", true)
            .billableUnit());
  }

  @Test
  void rejects_aRoutingKeyOutsideTheJobPlanesGrammar() {
    // A key the exchange binds no queue to is discarded by the broker without an error, which is
    // the same silent loss the routing table was introduced to end.
    assertThrows(
        IllegalArgumentException.class,
        () -> new CapabilityRoute("a.capability", "media.generate", null, "CHAT", "BATCH", true));
  }

  @Test
  void rejects_aRouteWithNothingToRouteOrNowhereToRouteIt() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CapabilityRoute(" ", "job.text.process", null, "CHAT", "BATCH", true));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CapabilityRoute("a.capability", null, null, "CHAT", "BATCH", true));
  }

  @Test
  void rejects_aBlankUnit_whichIsNeitherAbsentNorNamed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CapabilityRoute("a.capability", "job.text.process", "  ", "CHAT", "BATCH", true));
  }
}
