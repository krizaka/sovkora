package com.krizaka.sovkora.tools.infrastructure.adapter.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.krizaka.billing.domain.exception.InsufficientCreditsException;
import com.krizaka.billing.domain.model.BillableCapability;
import com.krizaka.billing.domain.model.ConsumptionReport;
import com.krizaka.billing.domain.model.CreditHoldCommand;
import com.krizaka.billing.domain.model.CreditHoldResponse;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import com.krizaka.sovkora.tools.application.port.law.ChatTokenMeter;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditHoldChatTokenMeterTest {

  private static final String RUN = "0b6c1c4e-0000-4000-8000-000000000002";
  private static final String ACTOR = "6f1d2a40-0000-4000-8000-0000000000bb";

  @Mock private CreditAuthorizationClient billing;

  private ChatTokenMeter meter() {
    return new CreditHoldChatTokenMeter(billing);
  }

  private static CreditHoldResponse granted(String holdId) {
    return new CreditHoldResponse(holdId, true, false, 30L, 970L, 2);
  }

  @Test
  void a_hold_is_the_chat_capability_and_exposes_the_ledgers_estimate() {
    when(billing.hold(any())).thenReturn(granted("c-1"));

    ChatTokenMeter.Hold hold = meter().hold(RUN, ACTOR);

    ArgumentCaptor<CreditHoldCommand> command = ArgumentCaptor.forClass(CreditHoldCommand.class);
    verify(billing).hold(command.capture());
    assertThat(command.getValue().capability()).isEqualTo(BillableCapability.CHAT);
    assertThat(command.getValue().actorId()).isEqualTo(ACTOR);
    assertThat(command.getValue().correlationId()).isEqualTo(RUN);
    assertThat(hold.estimatedCredits()).isEqualTo(30L);
  }

  @Test
  void settle_reports_the_tokens_as_a_measurement_under_the_chat_key() {
    when(billing.hold(any())).thenReturn(granted("c-1"));

    meter().hold(RUN, ACTOR).settle(TokenUsage.reported(900, 100, 1_000));

    ArgumentCaptor<ConsumptionReport> report = ArgumentCaptor.forClass(ConsumptionReport.class);
    verify(billing).settleMeasured(eq("c-1"), report.capture(), eq("chat:c-1"));
    assertThat(report.getValue().tokens()).isEqualTo(1_000L);
    assertThat(report.getValue().calls()).isNull();
  }

  @Test
  void a_hold_is_closed_once_whatever_is_called_after() {
    when(billing.hold(any())).thenReturn(granted("c-1"));
    ChatTokenMeter.Hold hold = meter().hold(RUN, ACTOR);

    hold.settle(TokenUsage.reported(1, 1, 2));
    hold.settle(TokenUsage.reported(1, 1, 2));
    hold.release("late");

    verify(billing).settleMeasured(eq("c-1"), any(), anyString());
    verify(billing, never()).release(anyString(), anyString());
  }

  @Test
  void unreported_tokens_release_the_hold_instead_of_billing_the_estimate() {
    when(billing.hold(any())).thenReturn(granted("c-1"));

    meter().hold(RUN, ACTOR).settle(TokenUsage.none());

    verify(billing).release("c-1", "no token usage reported");
    verify(billing, never()).settleMeasured(anyString(), any(), anyString());
  }

  @Test
  void release_closes_the_hold_with_the_reason() {
    when(billing.hold(any())).thenReturn(granted("c-1"));

    meter().hold(RUN, ACTOR).release("the grounded call did not run");

    verify(billing).release("c-1", "the grounded call did not run");
  }

  @Test
  void with_billing_off_there_is_nothing_to_settle_and_no_estimate() {
    when(billing.hold(any())).thenReturn(CreditHoldResponse.notMetered());

    ChatTokenMeter.Hold hold = meter().hold(RUN, ACTOR);
    hold.settle(TokenUsage.reported(1, 1, 2));

    assertThat(hold.estimatedCredits()).isZero();
    verify(billing, never()).settleMeasured(anyString(), any(), anyString());
  }

  @Test
  void insufficient_credits_are_the_budget_refusal() {
    when(billing.hold(any()))
        .thenThrow(new InsufficientCreditsException(ACTOR, BillableCapability.CHAT, 30, 0));

    assertThatThrownBy(() -> meter().hold(RUN, ACTOR))
        .isInstanceOf(ResearchMeter.BudgetExceededException.class);
  }

  @Test
  void no_actor_is_refused_before_billing_is_asked() {
    assertThatThrownBy(() -> meter().hold(RUN, " "))
        .isInstanceOf(ResearchMeter.BudgetExceededException.class);

    verifyNoInteractions(billing);
  }
}
