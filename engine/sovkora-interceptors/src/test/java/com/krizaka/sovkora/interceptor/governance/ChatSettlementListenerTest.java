package com.krizaka.sovkora.interceptor.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.krizaka.billing.domain.model.BillableUnit;
import com.krizaka.billing.domain.model.ConsumptionReport;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.sovkora.core.domain.event.ChatCompletedEvent;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatResponse;
import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatSettlementListenerTest {

  private static final String HOLD = "9f1c0a10-0000-4000-8000-000000000009";

  @Mock private CreditAuthorizationClient creditAuthorizationClient;

  private ChatSettlementListener listener() {
    return new ChatSettlementListener(creditAuthorizationClient);
  }

  private static InternalChatRequest request() {
    return new InternalChatRequest("hello", java.util.List.of(), null, Context.anonymous());
  }

  private static ChatCompletedEvent completed(TokenUsage usage, String holdId) {
    return new ChatCompletedEvent(
        request(), new InternalChatResponse("hi", "conv-1", usage, Map.of()), holdId);
  }

  @Test
  void constructor_rejectsNullClient() {
    assertThrows(NullPointerException.class, () -> new ChatSettlementListener(null));
  }

  @Test
  @DisplayName("a completed turn settles against the tokens the model actually produced")
  void settlesAgainstRealTokens() {
    listener().onChatCompleted(completed(TokenUsage.reported(1200, 2200, 3400), HOLD));

    ArgumentCaptor<ConsumptionReport> captor = ArgumentCaptor.forClass(ConsumptionReport.class);
    verify(creditAuthorizationClient).settleMeasured(eq(HOLD), captor.capture(), anyString());
    assertEquals(3400L, captor.getValue().tokens());
  }

  @Test
  @DisplayName("the report names no unit — the pricebook's row decides that")
  void reportsMeasurementsNotUnits() {
    listener().onChatCompleted(completed(TokenUsage.reported(100, 200, 300), HOLD));

    ArgumentCaptor<ConsumptionReport> captor = ArgumentCaptor.forClass(ConsumptionReport.class);
    verify(creditAuthorizationClient).settleMeasured(eq(HOLD), captor.capture(), anyString());
    // The ledger converts; the listener only measures. 300 tokens = 0.3 kilotokens.
    assertEquals(
        0,
        captor
            .getValue()
            .quantityFor(BillableUnit.KILOTOKEN)
            .orElseThrow()
            .compareTo(new BigDecimal("0.3")));
  }

  @Test
  @DisplayName("a turn whose provider reported nothing is released, never billed at its estimate")
  void releasesAnUnmeasuredTurn() {
    listener().onChatCompleted(completed(TokenUsage.none(), HOLD));

    verify(creditAuthorizationClient).release(eq(HOLD), anyString());
    verify(creditAuthorizationClient, never()).settleMeasured(anyString(), any(), anyString());
  }

  @Test
  @DisplayName("a genuine zero still settles — measured nothing is not the same as unmeasured")
  void settlesAReportedZero() {
    listener().onChatCompleted(completed(TokenUsage.reported(0, 0, 0), HOLD));

    verify(creditAuthorizationClient).settleMeasured(eq(HOLD), any(), anyString());
    verify(creditAuthorizationClient, never()).release(anyString(), anyString());
  }

  @Test
  @DisplayName("an unmetered turn touches the ledger not at all")
  void ignoresUnmeteredTurns() {
    listener().onChatCompleted(completed(TokenUsage.reported(100, 200, 300), null));

    verifyNoInteractions(creditAuthorizationClient);
  }

  @Test
  @DisplayName("a blank hold is treated as unmetered rather than settled against nothing")
  void ignoresBlankHolds() {
    listener().onChatCompleted(completed(TokenUsage.reported(100, 200, 300), "  "));

    verifyNoInteractions(creditAuthorizationClient);
  }

  @Test
  @DisplayName("the reservation is the replay guard, so a retried turn debits once")
  void idempotencyKeyIsDerivedFromTheHold() {
    listener().onChatCompleted(completed(TokenUsage.reported(100, 200, 300), HOLD));

    verify(creditAuthorizationClient).settleMeasured(eq(HOLD), any(), eq("chat:" + HOLD));
  }
}
