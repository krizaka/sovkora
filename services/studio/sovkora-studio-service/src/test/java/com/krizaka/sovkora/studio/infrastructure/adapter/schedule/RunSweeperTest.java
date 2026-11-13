package com.krizaka.sovkora.studio.infrastructure.adapter.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.studio.application.service.StudioRuntimeConfigService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RunSweeperTest {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final com.krizaka.sovkora.studio.application.service.RunSettlementService
      settlementService =
          mock(com.krizaka.sovkora.studio.application.service.RunSettlementService.class);
  private final StudioRuntimeConfigService runtimeConfigService =
      mock(StudioRuntimeConfigService.class);
  private final com.krizaka.sovkora.studio.application.service.RunAuditService runAuditService =
      mock(com.krizaka.sovkora.studio.application.service.RunAuditService.class);
  private final com.krizaka.sovkora.studio.application.service.StepReissueService reissueService =
      mock(com.krizaka.sovkora.studio.application.service.StepReissueService.class);
  private final com.krizaka.sovkora.studio.application.service.OutboxService outboxService =
      mock(com.krizaka.sovkora.studio.application.service.OutboxService.class);
  private final io.micrometer.core.instrument.simple.SimpleMeterRegistry meters =
      new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
  private final RunSweeper sweeper =
      new RunSweeper(
          jdbcTemplate,
          settlementService,
          runtimeConfigService,
          runAuditService,
          reissueService,
          outboxService,
          meters);

  /** The stub of the stale-step query: three lane strings, then the two ceilings and the cap. */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private org.mockito.stubbing.OngoingStubbing<List> staleQuery() {
    return when(
        jdbcTemplate.query(
            anyString(),
            any(RowMapper.class),
            anyString(),
            anyString(),
            anyString(),
            anyInt(),
            anyInt(),
            anyInt()));
  }

  @Test
  @DisplayName("A stalled run is failed AND its hold released — releasing is the whole point")
  void releasesTheHoldOfAStalledRun() {
    when(runtimeConfigService.intValue(anyString(), anyInt())).thenReturn(900);
    java.util.UUID runId = java.util.UUID.randomUUID();
    staleQuery().thenAnswer(invocation -> List.of(stale(runId, "BATCH")));

    sweeper.sweep();

    verify(settlementService).releaseAbandoned(eq("hold-7"), anyString());
  }

  @Test
  @DisplayName("Nothing stalled means nothing released")
  void quietWhenNothingIsStalled() {
    when(runtimeConfigService.intValue(anyString(), anyInt())).thenReturn(900);
    staleQuery().thenReturn(List.of());

    sweeper.sweep();

    verify(settlementService, never()).releaseAbandoned(anyString(), anyString());
  }

  @Test
  @DisplayName("Each lane's ceiling comes from its own row — both must be tunable at 2am")
  void eachLaneHasItsOwnTunableCeiling() {
    // ADR-067: one number could not serve both. The query carries BOTH, and the CASE in it picks
    // per step, so a batch step queued behind a 67-second image is judged by the batch ceiling
    // while a hung text step is reaped at the interactive one.
    when(runtimeConfigService.intValue("run.step-timeout-seconds.interactive", 300))
        .thenReturn(120);
    when(runtimeConfigService.intValue("run.step-timeout-seconds.batch", 1800)).thenReturn(3600);
    staleQuery().thenReturn(List.of());

    sweeper.sweep();

    verify(jdbcTemplate)
        .query(
            anyString(),
            any(RowMapper.class),
            anyString(),
            anyString(),
            anyString(),
            eq(120),
            eq(3600),
            anyInt());
  }

  @Test
  @DisplayName("The lane a step was dispatched in names the ceiling it is judged by")
  void theLaneNamesTheCeilingInTheFailure() throws Exception {
    when(runtimeConfigService.intValue("run.step-timeout-seconds.interactive", 300))
        .thenReturn(300);
    when(runtimeConfigService.intValue("run.step-timeout-seconds.batch", 1800)).thenReturn(1800);
    java.util.UUID runId = java.util.UUID.randomUUID();
    staleQuery().thenAnswer(invocation -> List.of(stale(runId, "INTERACTIVE")));

    sweeper.sweep();

    org.mockito.ArgumentCaptor<String> reason = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate)
        .update(
            org.mockito.ArgumentMatchers.contains("UPDATE studio_run SET status"),
            anyString(),
            reason.capture(),
            anyString(),
            eq(runId));
    assertThat(reason.getValue()).contains("INTERACTIVE").contains("300s");
  }

  /** Builds the private StaleRun the query maps to, via the sweeper's own row mapper. */
  private static Object stale(java.util.UUID runId, String lane) throws Exception {
    Class<?> type =
        Class.forName(
            "com.krizaka.sovkora.studio.infrastructure.adapter.schedule.RunSweeper$StaleRun");
    var constructor = type.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    return constructor.newInstance(
        runId, "hold-7", "actor-7", "compliance-check", "1.0.0", "corr-7", lane);
  }

  @Test
  @DisplayName(
      "[ADR-053] with the re-issue guard off a swept run declares TIMEOUT and appears in the"
          + " protected trail")
  void declaresItsCauseAndWritesTheTrail() {
    when(runtimeConfigService.intValue(anyString(), anyInt())).thenReturn(900);
    when(runtimeConfigService.intValue("run.step-max-reissues", 2)).thenReturn(0);
    java.util.UUID runId = java.util.UUID.randomUUID();
    staleQuery()
        .thenAnswer(
            invocation -> {
              RowMapper<?> mapper = invocation.getArgument(1);
              java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
              when(rs.getObject("id", java.util.UUID.class)).thenReturn(runId);
              when(rs.getString("hold_id")).thenReturn("hold-7");
              when(rs.getString("actor_id")).thenReturn("actor-7");
              when(rs.getString("studio_key")).thenReturn("compliance-check");
              when(rs.getString("blueprint_version")).thenReturn("1.0.0");
              when(rs.getString("correlation_id")).thenReturn("corr-7");
              when(rs.getString("lane")).thenReturn("BATCH");
              return java.util.List.of(mapper.mapRow(rs, 0));
            });

    sweeper.sweep();

    // Before this, a swept SENSITIVE run left NO audit row: the one terminal path that recorded
    // nothing, which is the hole a trail cannot have.
    org.mockito.Mockito.verify(runAuditService)
        .recordFinished(
            eq(runId),
            eq("actor-7"),
            eq("compliance-check"),
            eq(false),
            eq(com.krizaka.sovkora.jobs.api.domain.model.FailureCause.TIMEOUT));
  }

  @Test
  @DisplayName(
      "[SOV-105] a step silent past its last re-issue declares PLATFORM_UNAVAILABLE, is counted and"
          + " is announced")
  void aLostDispatchDeclaresPlatformUnavailable() throws Exception {
    when(runtimeConfigService.intValue(anyString(), anyInt())).thenReturn(900);
    when(runtimeConfigService.intValue("run.step-max-reissues", 2)).thenReturn(2);
    java.util.UUID runId = java.util.UUID.randomUUID();
    staleQuery().thenAnswer(invocation -> List.of(stale(runId, "BATCH")));

    sweeper.sweep();

    verify(reissueService).reissueSilentSteps(900, 900, 2);
    verify(runAuditService)
        .recordFinished(
            eq(runId),
            eq("actor-7"),
            eq("compliance-check"),
            eq(false),
            eq(com.krizaka.sovkora.jobs.api.domain.model.FailureCause.PLATFORM_UNAVAILABLE));
    verify(outboxService)
        .appendRunFailed(
            eq(runId), eq("actor-7"), eq("compliance-check"), eq("1.0.0"), eq("corr-7"));
    assertThat(meters.counter("sovkora.studio.run.past_bound", "bound", "step_ceiling").count())
        .isEqualTo(1.0);
  }
}
