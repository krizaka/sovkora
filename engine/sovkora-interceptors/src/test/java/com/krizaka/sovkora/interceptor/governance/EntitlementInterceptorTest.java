package com.krizaka.sovkora.interceptor.governance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.krizaka.billing.domain.exception.InsufficientCreditsException;
import com.krizaka.billing.domain.model.BillableCapability;
import com.krizaka.billing.domain.model.CreditHoldCommand;
import com.krizaka.billing.domain.model.CreditHoldResponse;
import com.krizaka.billing.domain.model.EntitlementSnapshot;
import com.krizaka.billing.domain.model.UnmeteredTurn;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.billing.domain.port.EntitlementProvider;
import com.krizaka.billing.domain.port.UnmeteredTurnRepository;
import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntitlementInterceptorTest {

  private static final String ACTOR = "550e8400-e29b-41d4-a716-446655440002";

  @Mock private EntitlementProvider entitlementProvider;
  @Mock private CreditAuthorizationClient creditAuthorizationClient;
  @Mock private UnmeteredTurnRepository unmeteredTurns;

  private EntitlementInterceptor interceptor() {
    return new EntitlementInterceptor(
        entitlementProvider, creditAuthorizationClient, unmeteredTurns);
  }

  private static PromptContext turn() {
    return new PromptContext("hello", Map.of("userId", ACTOR, "conversationId", "conv-1"));
  }

  private static EntitlementSnapshot plan(String planKey, Map<String, String> entitlements) {
    return new EntitlementSnapshot(
        ACTOR, planKey, entitlements, 500L, Instant.now().plusSeconds(60));
  }

  private static CreditHoldResponse granted(String holdId) {
    return new CreditHoldResponse(holdId, true, false, 2, 498, 1);
  }

  @Test
  void constructor_rejectsNullPorts() {
    assertThrows(
        NullPointerException.class,
        () -> new EntitlementInterceptor(null, creditAuthorizationClient, unmeteredTurns));
    assertThrows(
        NullPointerException.class,
        () -> new EntitlementInterceptor(entitlementProvider, null, unmeteredTurns));
  }

  @Test
  @DisplayName(
      "[ADR-044] a turn its orchestrator meters takes NO hold — but is still entitled-checked")
  void deferredMeteringTakesNoHold() {
    when(entitlementProvider.forActor(ACTOR))
        .thenReturn(plan("premium", Map.of("capability.chat", "true")));

    PromptContext step =
        new PromptContext(
            "hello",
            Map.of(
                "userId",
                ACTOR,
                "conversationId",
                "conv-1",
                "orazaka.metering.deferred",
                Boolean.TRUE));

    PromptContext result = interceptor().beforeExecution(step);

    // The regression: a Studio step flowed through this interceptor, which took a CHAT hold on the
    // assumption that a turn is synchronous. The run's aggregate settle then charged the same step
    // again — 16 credits for 8 inferences, eleven holds where three were authorised.
    verify(creditAuthorizationClient, never()).hold(any());
    assertNull(result.billingHoldId(), "no hold means nothing for the chat listener to settle");
    // The entitlement claim is NOT skipped: metering moves, permission does not.
    verify(entitlementProvider).forActor(ACTOR);
  }

  @Test
  @DisplayName("[ADR-044] a deferred turn is still refused when the plan excludes chat")
  void deferredMeteringStillEnforcesEntitlement() {
    when(entitlementProvider.forActor(ACTOR)).thenReturn(plan("free", Map.of()));

    PromptContext step =
        new PromptContext(
            "hello",
            Map.of("userId", ACTOR, "conversationId", "c", "orazaka.metering.deferred", true));

    assertThrows(PipelineShortCircuitException.class, () -> interceptor().beforeExecution(step));
    verify(creditAuthorizationClient, never()).hold(any());
  }

  @Test
  @DisplayName("an entitled actor with credits passes, and the hold is recorded for settlement")
  void allowsAndRecordsTheHold() {
    when(entitlementProvider.forActor(ACTOR))
        .thenReturn(plan("premium", Map.of("capability.chat", "true")));
    when(creditAuthorizationClient.hold(any())).thenReturn(granted("hold-7"));

    PromptContext result = interceptor().beforeExecution(turn());

    assertEquals("hold-7", result.billingHoldId());
  }

  @Test
  @DisplayName("the hold is priced by billing and correlated to the conversation for the roll-up")
  void holdCarriesCapabilityAndCorrelation() {
    when(entitlementProvider.forActor(ACTOR))
        .thenReturn(plan("premium", Map.of("capability.chat", "true")));
    when(creditAuthorizationClient.hold(any())).thenReturn(granted("hold-8"));

    interceptor().beforeExecution(turn());

    ArgumentCaptor<CreditHoldCommand> captor = ArgumentCaptor.forClass(CreditHoldCommand.class);
    verify(creditAuthorizationClient).hold(captor.capture());
    CreditHoldCommand command = captor.getValue();
    assertEquals(BillableCapability.CHAT, command.capability());
    assertEquals("conv-1", command.correlationId());
    assertEquals(0L, command.estimatedCredits());
    assertEquals(null, command.jobId(), "a synchronous turn has no async job behind it");
  }

  @Test
  @DisplayName("a plan without chat short-circuits before any credit is reserved")
  void refusesWhenTheCapabilityIsNotInThePlan() {
    when(entitlementProvider.forActor(ACTOR))
        .thenReturn(plan("free", Map.of("capability.chat", "false")));

    PipelineShortCircuitException refusal =
        assertThrows(
            PipelineShortCircuitException.class, () -> interceptor().beforeExecution(turn()));

    assertEquals("capability_not_in_plan", refusal.reason());
    verify(creditAuthorizationClient, never()).hold(any());
  }

  @Test
  @DisplayName("an empty balance short-circuits, carrying the ledger's refusal for the paywall")
  void refusesWhenCreditsAreExhausted() {
    when(entitlementProvider.forActor(ACTOR))
        .thenReturn(plan("premium", Map.of("capability.chat", "true")));
    when(creditAuthorizationClient.hold(any()))
        .thenThrow(
            new InsufficientCreditsException(
                ACTOR, BillableCapability.CHAT, 2, 0, List.of("top_up_credits")));

    PipelineShortCircuitException refusal =
        assertThrows(
            PipelineShortCircuitException.class, () -> interceptor().beforeExecution(turn()));

    assertEquals("insufficient_credits", refusal.reason());
    assertTrue(refusal.getCause() instanceof InsufficientCreditsException);
  }

  @Test
  @DisplayName("an unresolved snapshot never refuses — an outage must not lock users out")
  void doesNotGateWhenEntitlementsCouldNotBeRead() {
    when(entitlementProvider.forActor(ACTOR))
        .thenReturn(EntitlementSnapshot.unresolved(ACTOR, Instant.now().plusSeconds(5)));
    when(creditAuthorizationClient.hold(any())).thenReturn(granted("hold-9"));

    PromptContext result = interceptor().beforeExecution(turn());

    assertEquals("hold-9", result.billingHoldId());
  }

  @Test
  @DisplayName("with billing unwired the turn passes through unchanged")
  void unmeteredHoldLeavesTheContextAlone() {
    when(entitlementProvider.forActor(ACTOR))
        .thenReturn(EntitlementSnapshot.unresolved(ACTOR, Instant.now().plusSeconds(5)));
    when(creditAuthorizationClient.hold(any())).thenReturn(CreditHoldResponse.notMetered());

    PromptContext turn = turn();
    PromptContext result = interceptor().beforeExecution(turn);

    assertSame(turn, result);
    assertNull(result.billingHoldId());
  }

  @Test
  @DisplayName("a turn with no actor is not gated — there is no wallet and no plan to read")
  void skipsAnonymousTurns() {
    PromptContext anonymous = new PromptContext("hello", Map.of());

    PromptContext result = interceptor().beforeExecution(anonymous);

    assertSame(anonymous, result);
    verify(entitlementProvider, never()).forActor(anyString());
    verify(creditAuthorizationClient, never()).hold(any());
  }

  @Test
  @DisplayName("a turn outside a conversation still correlates, so the ledger accepts the hold")
  void fallsBackToTheActorAsCorrelation() {
    when(entitlementProvider.forActor(ACTOR))
        .thenReturn(plan("premium", Map.of("capability.chat", "true")));
    when(creditAuthorizationClient.hold(any())).thenReturn(granted("hold-10"));

    interceptor().beforeExecution(new PromptContext("hello", Map.of("userId", ACTOR)));

    ArgumentCaptor<CreditHoldCommand> captor = ArgumentCaptor.forClass(CreditHoldCommand.class);
    verify(creditAuthorizationClient).hold(captor.capture());
    assertEquals(ACTOR, captor.getValue().correlationId());
  }

  @Test
  @DisplayName("[ADR-064] an unreachable ledger serves the turn and records it as unmetered")
  void anUnreachableLedger_servesTheTurnAndRecordsItAsUnmetered() {
    // The posture is open and accounted — chosen here, no longer emergent from the executor's
    // catch. The record is made before the turn continues.
    when(entitlementProvider.forActor(ACTOR))
        .thenReturn(plan("pro", Map.of("capability.chat", "true")));
    when(creditAuthorizationClient.hold(any()))
        .thenThrow(new IllegalStateException("billing-service: connection refused"));

    PromptContext result = interceptor().beforeExecution(turn());

    assertNull(result.billingHoldId());
    ArgumentCaptor<UnmeteredTurn> recorded = ArgumentCaptor.forClass(UnmeteredTurn.class);
    verify(unmeteredTurns).record(recorded.capture());
    assertEquals(ACTOR, recorded.getValue().actorId());
    assertEquals(BillableCapability.CHAT, recorded.getValue().capability());
    assertEquals("conv-1", recorded.getValue().correlationId());
    // The failure's type, never its message: a message is text somebody else wrote.
    assertEquals("billing unreachable: IllegalStateException", recorded.getValue().reason());
  }

  @Test
  @DisplayName("[ADR-064] a turn that cannot be recorded is not served")
  void aTurnThatCannotBeRecorded_isNotServed() {
    when(entitlementProvider.forActor(ACTOR))
        .thenReturn(plan("pro", Map.of("capability.chat", "true")));
    when(creditAuthorizationClient.hold(any())).thenThrow(new IllegalStateException("down"));
    doThrow(new IllegalStateException("outbox down")).when(unmeteredTurns).record(any());

    assertThrows(IllegalStateException.class, () -> interceptor().beforeExecution(turn()));
  }

  @Test
  @DisplayName("[ADR-064] a control: what it did not anticipate stops the turn")
  void declaresItselfAControlThatFailsClosed() {
    assertTrue(interceptor().failsClosed());
  }

  @Test
  @DisplayName("the gate calls no model, so the AI kill-switch does not disable it")
  void isNotAiDependent() {
    assertFalse(interceptor().isAiDependent());
  }
}
