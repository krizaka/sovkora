package com.krizaka.sovkora.studio.infrastructure.adapter.schedule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.studio.application.service.RunInputPurgeService;
import com.krizaka.sovkora.studio.application.service.StudioRuntimeConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunInputPurgeSweeperTest {

  private final RunInputPurgeService inputPurge = mock(RunInputPurgeService.class);
  private final StudioRuntimeConfigService config = mock(StudioRuntimeConfigService.class);
  private final RunInputPurgeSweeper sweeper = new RunInputPurgeSweeper(inputPurge, config);

  @Test
  @DisplayName("[restricted#279] a sweep purges as many finished runs as its row allows")
  void aSweepIsBoundedByItsRow() {
    when(config.intValue("run.input-purge.batch-size", 100)).thenReturn(25);

    sweeper.sweep();

    verify(inputPurge).purgeFinished(25);
  }

  @Test
  @DisplayName("[restricted#279] a failed sweep is logged and left to the next one")
  void aFailedSweepDoesNotStopTheSchedule() {
    when(config.intValue("run.input-purge.batch-size", 100)).thenReturn(0);
    when(inputPurge.purgeFinished(1)).thenThrow(new IllegalStateException("database away"));

    assertThatCode(sweeper::sweep).doesNotThrowAnyException();
    verify(inputPurge).purgeFinished(1);
  }
}
