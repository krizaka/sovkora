package com.krizaka.sovkora.studio.infrastructure.adapter.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.krizaka.sovkora.studio.application.service.StalledRunService;
import com.krizaka.sovkora.studio.infrastructure.config.DispatchStallProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StalledRunSweeperTest {

  @Test
  @DisplayName("the cutoff is the injected clock minus the configured timeout, the batch its cap")
  void usesTheInjectedClockAndTheConfiguredBounds() {
    StalledRunService service = mock(StalledRunService.class);
    Instant now = Instant.parse("2026-11-12T10:00:00Z");
    StalledRunSweeper sweeper =
        new StalledRunSweeper(
            service,
            new DispatchStallProperties(Duration.ofMinutes(5), 20),
            Clock.fixed(now, java.time.ZoneOffset.UTC));

    sweeper.sweep();

    verify(service).failUndispatched(Instant.parse("2026-11-12T09:55:00Z"), 20);
  }
}
