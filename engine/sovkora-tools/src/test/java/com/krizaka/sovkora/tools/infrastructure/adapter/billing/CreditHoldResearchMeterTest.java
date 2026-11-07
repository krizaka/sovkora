package com.krizaka.sovkora.tools.infrastructure.adapter.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.krizaka.billing.domain.exception.InsufficientCreditsException;
import com.krizaka.billing.domain.model.BillableCapability;
import com.krizaka.billing.domain.model.ConsumptionReport;
import com.krizaka.billing.domain.model.CreditHoldCommand;
import com.krizaka.billing.domain.model.CreditHoldResponse;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditHoldResearchMeterTest {

  private static final String RUN = "0b6c1c4e-0000-4000-8000-000000000002";
  private static final String ACTOR = "6f1d2a40-0000-4000-8000-0000000000bb";

  @Mock private CreditAuthorizationClient billing;

  private ResearchMeter meter() {
    return new CreditHoldResearchMeter(billing);
  }

  private static CreditHoldResponse granted(String holdId, boolean dryRun) {
    return new CreditHoldResponse(holdId, true, dryRun, 10L, 990L, 2);
  }

  @Test
  void a_hold_is_one_research_call_priced_by_the_provider_key() {
    when(billing.hold(any())).thenReturn(granted("r-1", false));

    meter().hold(RUN, ACTOR, "law-corpus");

    ArgumentCaptor<CreditHoldCommand> command = ArgumentCaptor.forClass(CreditHoldCommand.class);
    verify(billing).hold(command.capture());
    assertThat(command.getValue().actorId()).isEqualTo(ACTOR);
    assertThat(command.getValue().capability()).isEqualTo(BillableCapability.RESEARCH);
    assertThat(command.getValue().modelName()).isEqualTo("law-corpus");
    assertThat(command.getValue().correlationId()).isEqualTo(RUN);
    assertThat(command.getValue().jobId()).isNull();
    assertThat(command.getValue().estimatedCredits()).isZero();
  }

  @Test
  void settle_reports_one_call_under_a_key_derived_from_the_hold() {
    when(billing.hold(any())).thenReturn(granted("r-1", false));

    meter().hold(RUN, ACTOR, "law-corpus").settle();

    ArgumentCaptor<ConsumptionReport> report = ArgumentCaptor.forClass(ConsumptionReport.class);
    verify(billing).settleMeasured(eq("r-1"), report.capture(), eq("research:r-1"));
    assertThat(report.getValue().calls()).isEqualTo(1L);
  }

  @Test
  void release_closes_the_hold_with_the_reason() {
    when(billing.hold(any())).thenReturn(granted("r-1", false));

    meter().hold(RUN, ACTOR, "law-corpus").release("the grounded call failed");

    verify(billing).release("r-1", "the grounded call failed");
    verify(billing, never()).settleMeasured(anyString(), any(), anyString());
  }

  @Test
  void a_shadow_grant_still_settles_its_real_hold() {
    when(billing.hold(any())).thenReturn(granted("shadow-r", true));

    meter().hold(RUN, ACTOR, "law-corpus").settle();

    verify(billing).settleMeasured(eq("shadow-r"), any(), eq("research:shadow-r"));
  }

  @Test
  void with_billing_off_nothing_is_settled() {
    when(billing.hold(any())).thenReturn(CreditHoldResponse.notMetered());

    meter().hold(RUN, ACTOR, "law-corpus").settle();

    verify(billing, never()).settleMeasured(anyString(), any(), anyString());
  }

  @Test
  void insufficient_credits_are_the_ports_budget_refusal() {
    when(billing.hold(any()))
        .thenThrow(new InsufficientCreditsException(ACTOR, BillableCapability.RESEARCH, 10, 0));

    assertThatThrownBy(() -> meter().hold(RUN, ACTOR, "law-corpus"))
        .isInstanceOf(ResearchMeter.BudgetExceededException.class)
        .hasNoCause()
        .satisfies(refusal -> assertThat(refusal.getMessage()).doesNotContain(ACTOR));
  }

  @Test
  void a_call_with_no_billable_actor_is_refused_before_billing_is_asked() {
    assertThatThrownBy(() -> meter().hold(RUN, null, "law-corpus"))
        .isInstanceOf(ResearchMeter.BudgetExceededException.class);

    verifyNoInteractions(billing);
  }

  @Test
  void a_call_with_no_run_correlates_on_the_actor() {
    when(billing.hold(any())).thenReturn(granted("r-1", false));

    meter().hold(null, ACTOR, "law-corpus");

    ArgumentCaptor<CreditHoldCommand> command = ArgumentCaptor.forClass(CreditHoldCommand.class);
    verify(billing).hold(command.capture());
    assertThat(command.getValue().correlationId()).isEqualTo(ACTOR);
  }
}
