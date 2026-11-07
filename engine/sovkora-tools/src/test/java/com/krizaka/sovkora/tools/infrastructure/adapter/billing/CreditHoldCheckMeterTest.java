package com.krizaka.sovkora.tools.infrastructure.adapter.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.krizaka.billing.domain.exception.InsufficientCreditsException;
import com.krizaka.billing.domain.model.BillableCapability;
import com.krizaka.billing.domain.model.BillableUnit;
import com.krizaka.billing.domain.model.ConsumptionReport;
import com.krizaka.billing.domain.model.CreditHoldCommand;
import com.krizaka.billing.domain.model.CreditHoldResponse;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.sovkora.core.domain.model.checks.CheckPurpose;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckMeter;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditHoldCheckMeterTest {

  private static final UUID RUN = UUID.fromString("0b6c1c4e-0000-4000-8000-000000000001");
  private static final String ACTOR = "6f1d2a40-0000-4000-8000-0000000000aa";
  private static final String HOLD = "hold-1";

  @Mock private CreditAuthorizationClient billing;

  private CheckMeter meter() {
    return new CreditHoldCheckMeter(billing);
  }

  private static CreditHoldResponse granted(String holdId, boolean dryRun) {
    return new CreditHoldResponse(holdId, true, dryRun, 50L, 950L, 2);
  }

  @Test
  void a_hold_is_one_external_check_call_priced_by_the_connectors_provider() {
    when(billing.hold(any())).thenReturn(granted(HOLD, false));

    meter().hold(RUN, ACTOR, "mock", CheckPurpose.COURT_RECORDS);

    ArgumentCaptor<CreditHoldCommand> command = ArgumentCaptor.forClass(CreditHoldCommand.class);
    verify(billing).hold(command.capture());
    assertThat(command.getValue().actorId()).isEqualTo(ACTOR);
    assertThat(command.getValue().capability()).isEqualTo(BillableCapability.EXTERNAL_CHECK);
    assertThat(command.getValue().modelName()).isEqualTo("mock");
    assertThat(command.getValue().correlationId()).isEqualTo(RUN.toString());
    // No job id: the run's own job is settled by the job's hold, never by this one.
    assertThat(command.getValue().jobId()).isNull();
    // Zero asks the ledger for the pricebook row's estimate: one CALL.
    assertThat(command.getValue().estimatedCredits()).isZero();
  }

  @Test
  void a_platform_call_with_no_run_correlates_on_the_actor() {
    when(billing.hold(any())).thenReturn(granted(HOLD, false));

    meter().hold(null, ACTOR, "mock", CheckPurpose.IDENTITY);

    ArgumentCaptor<CreditHoldCommand> command = ArgumentCaptor.forClass(CreditHoldCommand.class);
    verify(billing).hold(command.capture());
    assertThat(command.getValue().correlationId()).isEqualTo(ACTOR);
  }

  @Test
  void settle_reports_one_call_under_a_key_derived_from_the_hold() {
    when(billing.hold(any())).thenReturn(granted(HOLD, false));

    meter().hold(RUN, ACTOR, "mock", CheckPurpose.CREDIT).settle();

    ArgumentCaptor<ConsumptionReport> report = ArgumentCaptor.forClass(ConsumptionReport.class);
    verify(billing).settleMeasured(eq(HOLD), report.capture(), eq("external-check:" + HOLD));
    assertThat(report.getValue().calls()).isEqualTo(1L);
    assertThat(report.getValue().quantityFor(BillableUnit.CALL)).contains(BigDecimal.ONE);
    assertThat(report.getValue().tokens()).isNull();
  }

  @Test
  void the_settle_key_is_stable_for_one_hold_and_distinct_between_holds() {
    when(billing.hold(any())).thenReturn(granted("hold-a", false), granted("hold-b", false));

    meter().hold(RUN, ACTOR, "mock", CheckPurpose.CREDIT).settle();
    meter().hold(RUN, ACTOR, "mock", CheckPurpose.CREDIT).settle();

    verify(billing).settleMeasured(eq("hold-a"), any(), eq("external-check:hold-a"));
    verify(billing).settleMeasured(eq("hold-b"), any(), eq("external-check:hold-b"));
  }

  @Test
  void release_closes_the_hold_with_the_reason_and_charges_nothing() {
    when(billing.hold(any())).thenReturn(granted(HOLD, false));

    meter().hold(RUN, ACTOR, "mock", CheckPurpose.COURT_RECORDS).release("timeout");

    verify(billing).release(HOLD, "timeout");
    verify(billing, never()).settleMeasured(anyString(), any(), anyString());
  }

  @Test
  void a_hold_closes_once_a_settle_after_a_release_is_ignored() {
    when(billing.hold(any())).thenReturn(granted(HOLD, false));

    CheckMeter.Hold hold = meter().hold(RUN, ACTOR, "mock", CheckPurpose.COURT_RECORDS);
    hold.release("timeout");
    hold.settle();
    hold.release("timeout");

    verify(billing).release(HOLD, "timeout");
    verify(billing, never()).settleMeasured(anyString(), any(), anyString());
  }

  @Test
  void a_shadow_grant_still_settles_and_releases_its_real_hold() {
    when(billing.hold(any())).thenReturn(granted("shadow-1", true), granted("shadow-2", true));

    meter().hold(RUN, ACTOR, "mock", CheckPurpose.CREDIT).settle();
    meter().hold(RUN, ACTOR, "mock", CheckPurpose.CREDIT).release("provider error");

    verify(billing).settleMeasured(eq("shadow-1"), any(), eq("external-check:shadow-1"));
    verify(billing).release("shadow-2", "provider error");
  }

  @Test
  void with_billing_off_nothing_is_held_so_nothing_is_settled_or_released() {
    when(billing.hold(any())).thenReturn(CreditHoldResponse.notMetered());

    CheckMeter.Hold hold = meter().hold(RUN, ACTOR, "mock", CheckPurpose.CREDIT);
    hold.settle();
    hold.release("timeout");

    verify(billing, never()).settleMeasured(anyString(), any(), anyString());
    verify(billing, never()).release(anyString(), anyString());
  }

  @Test
  void insufficient_credits_are_the_ports_budget_refusal_with_no_actor_in_it() {
    when(billing.hold(any()))
        .thenThrow(
            new InsufficientCreditsException(ACTOR, BillableCapability.EXTERNAL_CHECK, 50, 3));

    assertThatThrownBy(() -> meter().hold(RUN, ACTOR, "mock", CheckPurpose.COURT_RECORDS))
        .isInstanceOf(CheckMeter.BudgetExhaustedException.class)
        .hasNoCause()
        .satisfies(
            refusal ->
                assertThat(refusal.getMessage())
                    .doesNotContain(ACTOR)
                    .doesNotContain(RUN.toString()));
  }

  @Test
  void a_grant_that_is_not_granted_is_released_and_refused() {
    when(billing.hold(any())).thenReturn(new CreditHoldResponse(HOLD, false, false, 50L, 0L, 2));

    assertThatThrownBy(() -> meter().hold(RUN, ACTOR, "mock", CheckPurpose.COURT_RECORDS))
        .isInstanceOf(CheckMeter.BudgetExhaustedException.class);

    ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
    verify(billing).release(eq(HOLD), reason.capture());
    assertThat(reason.getValue()).doesNotContain(ACTOR).doesNotContain(RUN.toString());
  }

  @Test
  void a_call_with_no_billable_actor_is_refused_before_billing_is_asked() {
    assertThatThrownBy(() -> meter().hold(RUN, null, "mock", CheckPurpose.COURT_RECORDS))
        .isInstanceOf(CheckMeter.BudgetExhaustedException.class);
    assertThatThrownBy(() -> meter().hold(RUN, " ", "mock", CheckPurpose.COURT_RECORDS))
        .isInstanceOf(CheckMeter.BudgetExhaustedException.class);

    verifyNoInteractions(billing);
  }

  @Test
  void billing_unreachable_is_not_a_budget_refusal_it_propagates_and_no_call_is_made() {
    when(billing.hold(any())).thenThrow(new IllegalStateException("connection refused"));

    assertThatThrownBy(() -> meter().hold(RUN, ACTOR, "mock", CheckPurpose.COURT_RECORDS))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void the_meter_and_its_hold_say_nothing_of_the_actor_or_the_run() {
    when(billing.hold(any())).thenReturn(granted(HOLD, false));

    CheckMeter.Hold hold = meter().hold(RUN, ACTOR, "mock", CheckPurpose.COURT_RECORDS);

    assertThat(String.valueOf(hold)).doesNotContain(ACTOR).doesNotContain(RUN.toString());
    assertThat(String.valueOf(meter())).doesNotContain(ACTOR);
  }
}
