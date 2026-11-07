package com.krizaka.sovkora.automationservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.automationservice.domain.exception.WebhookRejectedException;
import com.krizaka.sovkora.automationservice.domain.model.IngressResult;
import com.krizaka.sovkora.automationservice.domain.model.Rejection;
import com.krizaka.sovkora.automationservice.domain.model.RunStatus;
import com.krizaka.sovkora.automationservice.domain.model.SettingKey;
import com.krizaka.sovkora.automationservice.domain.model.TriggerCredentials;
import com.krizaka.sovkora.automationservice.domain.model.WebhookSecret;
import com.krizaka.sovkora.automationservice.domain.model.WebhookSignature;
import com.krizaka.sovkora.automationservice.domain.port.BodySource;
import com.krizaka.sovkora.automationservice.domain.port.CallLimiter;
import com.krizaka.sovkora.automationservice.domain.port.RunDispatcher;
import com.krizaka.sovkora.automationservice.domain.port.RunRepository;
import com.krizaka.sovkora.automationservice.domain.port.SecretSealer;
import com.krizaka.sovkora.automationservice.domain.port.WebhookCredentialStore;
import com.krizaka.sovkora.automationservice.domain.port.WebhookRunLedger;
import com.krizaka.sovkora.automationservice.support.FixedClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * The ingress decides, in order and before any work: size, global rate, trigger, per-trigger rate,
 * headers and timestamp, signature, replay, run rate, daily budget — and only then writes.
 */
class WebhookIngressServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
  private static final byte[] BODY = "{\"order\":42}".getBytes(StandardCharsets.UTF_8);

  private final WebhookCredentialStore triggers = mock(WebhookCredentialStore.class);
  private final WebhookRunLedger ledger = mock(WebhookRunLedger.class);
  private final RunRepository runs = mock(RunRepository.class);
  private final CallLimiter limiter = mock(CallLimiter.class);
  private final RunDispatcher dispatcher = mock(RunDispatcher.class);
  private final AutomationSettingsService settings = mock(AutomationSettingsService.class);
  private final FixedClock clock = new FixedClock(NOW);

  /** A sealer that is the identity: the crypto has its own test; this one is about decisions. */
  private final SecretSealer sealer =
      new SecretSealer() {
        @Override
        public String seal(UUID scope, String plaintext) {
          return "sealed:" + scope + ":" + plaintext;
        }

        @Override
        public String open(UUID scope, String sealed) {
          String prefix = "sealed:" + scope + ":";
          if (!sealed.startsWith(prefix)) {
            throw new IllegalStateException("does not open");
          }
          return sealed.substring(prefix.length());
        }
      };

  private final WebhookSecret secret = WebhookSecret.generate();
  private final UUID triggerId = UUID.randomUUID();
  private final UUID automationId = UUID.randomUUID();
  private WebhookIngressService ingress;

  @BeforeEach
  void setUp() {
    ingress =
        new WebhookIngressService(
            triggers,
            ledger,
            runs,
            sealer,
            limiter,
            dispatcher,
            settings,
            clock,
            new ObjectMapper());
    for (SettingKey key : SettingKey.values()) {
      when(settings.value(key)).thenReturn(key.defaultValue());
    }
    when(limiter.tryAcquire(anyString(), anyInt())).thenReturn(new CallLimiter.Decision(true, 0));
    when(triggers.credentials(triggerId)).thenReturn(Optional.of(credentials(true)));
    when(ledger.existingWebhookRun(any(), anyString())).thenReturn(Optional.empty());
    when(ledger.recordWebhookRun(any(), any(), any(), anyString(), any(), anyInt()))
        .thenReturn(true);
  }

  private TriggerCredentials credentials(boolean active) {
    return new TriggerCredentials(
        triggerId, automationId, active, sealer.seal(triggerId, secret.reveal()), null, null);
  }

  private String sign(WebhookSecret with, String id, long timestamp, byte[] body) {
    return WebhookSignature.sign(with.key(), id, Long.toString(timestamp), body);
  }

  private IngressResult call(String id, long timestamp, String signature, byte[] body) {
    return ingress.accept(
        triggerId.toString(),
        id,
        Long.toString(timestamp),
        signature,
        body.length,
        max -> body.length > max ? null : body);
  }

  private WebhookRejectedException refused(Runnable call) {
    return org.junit.jupiter.api.Assertions.assertThrows(WebhookRejectedException.class, call::run);
  }

  @Test
  @DisplayName("a valid signature records a queued run, hands it to the dispatcher, and answers it")
  void accepts() {
    IngressResult result =
        call(
            "msg_1", NOW.getEpochSecond(), sign(secret, "msg_1", NOW.getEpochSecond(), BODY), BODY);

    assertThat(result.status()).isEqualTo(RunStatus.QUEUED);
    verify(ledger)
        .recordWebhookRun(
            eq(result.runId()),
            eq(automationId),
            eq(triggerId),
            eq("msg_1"),
            eq("{\"order\":42}"),
            eq(BODY.length));
    verify(dispatcher).submit(result.runId());
  }

  @Test
  @DisplayName("a body that is not a JSON object is accepted but not kept")
  void nonObjectBodyIsNotKept() {
    byte[] text = "hello".getBytes(StandardCharsets.UTF_8);
    IngressResult result =
        call(
            "msg_2", NOW.getEpochSecond(), sign(secret, "msg_2", NOW.getEpochSecond(), text), text);
    verify(ledger)
        .recordWebhookRun(
            eq(result.runId()), any(), any(), eq("msg_2"), eq((String) null), eq(text.length));
  }

  @Test
  @DisplayName("a wrong signature is refused as unauthenticated and nothing is written or started")
  void wrongSignature() {
    WebhookSecret other = WebhookSecret.generate();
    WebhookRejectedException e =
        refused(
            () ->
                call(
                    "msg_3",
                    NOW.getEpochSecond(),
                    sign(other, "msg_3", NOW.getEpochSecond(), BODY),
                    BODY));
    assertThat(e.rejection()).isEqualTo(Rejection.UNAUTHENTICATED);
    verify(ledger, never()).recordWebhookRun(any(), any(), any(), any(), any(), anyInt());
    verify(dispatcher, never()).submit(any());
    verify(ledger, never()).existingWebhookRun(any(), any());
  }

  @Test
  @DisplayName("a body changed after signing, or another id, fails the signature")
  void tampering() {
    String signature = sign(secret, "msg_4", NOW.getEpochSecond(), BODY);
    byte[] changed = "{\"order\":43}".getBytes(StandardCharsets.UTF_8);
    assertThat(refused(() -> call("msg_4", NOW.getEpochSecond(), signature, changed)).rejection())
        .isEqualTo(Rejection.UNAUTHENTICATED);
    assertThat(refused(() -> call("msg_5", NOW.getEpochSecond(), signature, BODY)).rejection())
        .isEqualTo(Rejection.UNAUTHENTICATED);
  }

  @Test
  @DisplayName(
      "a timestamp outside the tolerance, either way, is refused even when the signature is good")
  void staleTimestamp() {
    long old = NOW.minus(Duration.ofMinutes(6)).getEpochSecond();
    long future = NOW.plus(Duration.ofMinutes(6)).getEpochSecond();
    assertThat(
            refused(() -> call("msg_6", old, sign(secret, "msg_6", old, BODY), BODY)).rejection())
        .isEqualTo(Rejection.UNAUTHENTICATED);
    assertThat(
            refused(() -> call("msg_7", future, sign(secret, "msg_7", future, BODY), BODY))
                .rejection())
        .isEqualTo(Rejection.UNAUTHENTICATED);
    long edge = NOW.minus(Duration.ofSeconds(299)).getEpochSecond();
    assertThat(call("msg_8", edge, sign(secret, "msg_8", edge, BODY), BODY).status())
        .isEqualTo(RunStatus.QUEUED);
  }

  @Test
  @DisplayName(
      "missing or malformed headers, and a timestamp that is not a number, are unauthenticated")
  void headers() {
    assertThat(
            refused(
                    () ->
                        ingress.accept(
                            triggerId.toString(), null, "1", "v1,x", BODY.length, max -> BODY))
                .rejection())
        .isEqualTo(Rejection.UNAUTHENTICATED);
    assertThat(
            refused(
                    () ->
                        ingress.accept(
                            triggerId.toString(), "msg 9", "1", "v1,x", BODY.length, max -> BODY))
                .rejection())
        .isEqualTo(Rejection.UNAUTHENTICATED);
    assertThat(
            refused(
                    () ->
                        ingress.accept(
                            triggerId.toString(),
                            "msg_9",
                            "yesterday",
                            "v1,x",
                            BODY.length,
                            max -> BODY))
                .rejection())
        .isEqualTo(Rejection.UNAUTHENTICATED);
    assertThat(
            refused(
                    () ->
                        ingress.accept(
                            triggerId.toString(),
                            "msg_9",
                            Long.toString(NOW.getEpochSecond()),
                            null,
                            BODY.length,
                            max -> BODY))
                .rejection())
        .isEqualTo(Rejection.UNAUTHENTICATED);
  }

  @Test
  @DisplayName(
      "an unknown trigger, a malformed id and a disabled trigger are refused like a bad signature")
  void unknownOrDisabled() {
    String signature = sign(secret, "msg_10", NOW.getEpochSecond(), BODY);
    UUID unknown = UUID.randomUUID();
    when(triggers.credentials(unknown)).thenReturn(Optional.empty());
    for (String id : new String[] {unknown.toString(), "not-a-uuid", "", "../../etc"}) {
      WebhookRejectedException e =
          refused(
              () ->
                  ingress.accept(
                      id,
                      "msg_10",
                      Long.toString(NOW.getEpochSecond()),
                      signature,
                      BODY.length,
                      max -> BODY));
      assertThat(e.rejection()).as(id).isEqualTo(Rejection.UNAUTHENTICATED);
    }
    when(triggers.credentials(triggerId)).thenReturn(Optional.of(credentials(false)));
    assertThat(refused(() -> call("msg_10", NOW.getEpochSecond(), signature, BODY)).rejection())
        .isEqualTo(Rejection.UNAUTHENTICATED);
    verify(dispatcher, never()).submit(any());
  }

  @Test
  @DisplayName(
      "an oversized declared body is refused first, before the limiter, the database or the body")
  void declaredTooLarge() {
    BodySource unread =
        max -> {
          throw new AssertionError("the body must not be read");
        };
    WebhookRejectedException e =
        refused(() -> ingress.accept(triggerId.toString(), "m", "1", "v1,x", 70_000, unread));
    assertThat(e.rejection()).isEqualTo(Rejection.PAYLOAD_TOO_LARGE);
    verify(limiter, never()).tryAcquire(anyString(), anyInt());
    verify(triggers, never()).credentials(any());
  }

  @Test
  @DisplayName("an undeclared body that turns out too large is refused too")
  void undeclaredTooLarge() {
    WebhookRejectedException e =
        refused(
            () ->
                ingress.accept(
                    triggerId.toString(),
                    "m",
                    Long.toString(NOW.getEpochSecond()),
                    "v1,x",
                    -1,
                    max -> null));
    assertThat(e.rejection()).isEqualTo(Rejection.PAYLOAD_TOO_LARGE);
  }

  @Test
  @DisplayName(
      "a verified call that reuses a webhook-id is a replay, and carries the first run's id")
  void replay() {
    UUID first = UUID.randomUUID();
    when(ledger.existingWebhookRun(triggerId, "msg_11")).thenReturn(Optional.of(first));
    WebhookRejectedException e =
        refused(
            () ->
                call(
                    "msg_11",
                    NOW.getEpochSecond(),
                    sign(secret, "msg_11", NOW.getEpochSecond(), BODY),
                    BODY));
    assertThat(e.rejection()).isEqualTo(Rejection.REPLAYED);
    assertThat(e.existingRunId()).isEqualTo(first);
    verify(dispatcher, never()).submit(any());
  }

  @Test
  @DisplayName(
      "a replay is never revealed to an unverified caller: a bad signature on a used id is still 401")
  void replayIsNotAnOracle() {
    when(ledger.existingWebhookRun(triggerId, "msg_12")).thenReturn(Optional.of(UUID.randomUUID()));
    WebhookSecret other = WebhookSecret.generate();
    assertThat(
            refused(
                    () ->
                        call(
                            "msg_12",
                            NOW.getEpochSecond(),
                            sign(other, "msg_12", NOW.getEpochSecond(), BODY),
                            BODY))
                .rejection())
        .isEqualTo(Rejection.UNAUTHENTICATED);
    verify(ledger, never()).existingWebhookRun(any(), any());
  }

  @Test
  @DisplayName(
      "losing the race for a webhook-id to a parallel delivery is a replay, not a second run")
  void lostRace() {
    UUID winner = UUID.randomUUID();
    when(ledger.recordWebhookRun(any(), any(), any(), anyString(), any(), anyInt()))
        .thenReturn(false);
    when(ledger.existingWebhookRun(triggerId, "msg_13"))
        .thenReturn(Optional.empty(), Optional.of(winner));
    WebhookRejectedException e =
        refused(
            () ->
                call(
                    "msg_13",
                    NOW.getEpochSecond(),
                    sign(secret, "msg_13", NOW.getEpochSecond(), BODY),
                    BODY));
    assertThat(e.rejection()).isEqualTo(Rejection.REPLAYED);
    assertThat(e.existingRunId()).isEqualTo(winner);
    verify(dispatcher, never()).submit(any());
  }

  @Test
  @DisplayName(
      "an empty bucket answers 429 with the time to wait, at the global, per-trigger and run levels")
  void rateLimits() {
    when(limiter.tryAcquire("hooks:all", SettingKey.WEBHOOK_GLOBAL_PER_MINUTE.defaultValue()))
        .thenReturn(new CallLimiter.Decision(false, 7));
    WebhookRejectedException global = refused(() -> call("m", NOW.getEpochSecond(), "v1,x", BODY));
    assertThat(global.rejection()).isEqualTo(Rejection.RATE_LIMITED);
    assertThat(global.retryAfterSeconds()).isEqualTo(7);
    verify(triggers, never()).credentials(any());

    when(limiter.tryAcquire("hooks:all", SettingKey.WEBHOOK_GLOBAL_PER_MINUTE.defaultValue()))
        .thenReturn(new CallLimiter.Decision(true, 0));
    when(limiter.tryAcquire(
            "hooks:pre:" + triggerId, SettingKey.WEBHOOK_PREVERIFY_PER_MINUTE.defaultValue()))
        .thenReturn(new CallLimiter.Decision(false, 3));
    assertThat(refused(() -> call("m", NOW.getEpochSecond(), "v1,x", BODY)).retryAfterSeconds())
        .isEqualTo(3);

    when(limiter.tryAcquire(
            "hooks:pre:" + triggerId, SettingKey.WEBHOOK_PREVERIFY_PER_MINUTE.defaultValue()))
        .thenReturn(new CallLimiter.Decision(true, 0));
    when(limiter.tryAcquire(
            "hooks:run:" + triggerId, SettingKey.WEBHOOK_RUNS_PER_MINUTE.defaultValue()))
        .thenReturn(new CallLimiter.Decision(false, 2));
    WebhookRejectedException runs =
        refused(
            () ->
                call(
                    "msg_14",
                    NOW.getEpochSecond(),
                    sign(secret, "msg_14", NOW.getEpochSecond(), BODY),
                    BODY));
    assertThat(runs.rejection()).isEqualTo(Rejection.RATE_LIMITED);
    verify(dispatcher, never()).submit(any());
  }

  @Test
  @DisplayName(
      "an automation that spent its day's runs answers 429 until tomorrow, and the id is not burnt")
  void dailyBudget() {
    when(runs.runsSince(eq(automationId), any()))
        .thenReturn(SettingKey.MAX_RUNS_PER_DAY.defaultValue());
    WebhookRejectedException e =
        refused(
            () ->
                call(
                    "msg_15",
                    NOW.getEpochSecond(),
                    sign(secret, "msg_15", NOW.getEpochSecond(), BODY),
                    BODY));
    assertThat(e.rejection()).isEqualTo(Rejection.DAILY_BUDGET);
    assertThat(e.retryAfterSeconds()).isEqualTo(12 * 3600);
    verify(ledger, never()).recordWebhookRun(any(), any(), any(), any(), any(), anyInt());
  }

  @Test
  @DisplayName("during a rotation grace the previous secret still verifies; after it, it does not")
  void rotationGrace() {
    WebhookSecret previous = WebhookSecret.generate();
    when(triggers.credentials(triggerId))
        .thenReturn(
            Optional.of(
                new TriggerCredentials(
                    triggerId,
                    automationId,
                    true,
                    sealer.seal(triggerId, secret.reveal()),
                    sealer.seal(triggerId, previous.reveal()),
                    NOW.plus(Duration.ofHours(1)))));
    assertThat(
            call(
                    "msg_16",
                    NOW.getEpochSecond(),
                    sign(previous, "msg_16", NOW.getEpochSecond(), BODY),
                    BODY)
                .status())
        .isEqualTo(RunStatus.QUEUED);
    assertThat(
            call(
                    "msg_17",
                    NOW.getEpochSecond(),
                    sign(secret, "msg_17", NOW.getEpochSecond(), BODY),
                    BODY)
                .status())
        .isEqualTo(RunStatus.QUEUED);

    clock.advance(Duration.ofHours(2));
    long later = clock.instant().getEpochSecond();
    assertThat(
            refused(() -> call("msg_18", later, sign(previous, "msg_18", later, BODY), BODY))
                .rejection())
        .isEqualTo(Rejection.UNAUTHENTICATED);
  }

  @Test
  @DisplayName("a secret that cannot be opened verifies nothing: unauthenticated, not an error")
  void unopenableSecret() {
    when(triggers.credentials(triggerId))
        .thenReturn(
            Optional.of(
                new TriggerCredentials(
                    triggerId, automationId, true, "sealed:other:whsec_x", null, null)));
    assertThat(
            refused(
                    () ->
                        call(
                            "msg_19",
                            NOW.getEpochSecond(),
                            sign(secret, "msg_19", NOW.getEpochSecond(), BODY),
                            BODY))
                .rejection())
        .isEqualTo(Rejection.UNAUTHENTICATED);
  }

  @Test
  @DisplayName("the secret never appears in what the exception or the credentials print")
  void noSecretInStrings() {
    TriggerCredentials credentials = credentials(true);
    assertThat(credentials.toString()).doesNotContain(secret.reveal()).doesNotContain("sealed:");
    assertThatThrownBy(() -> call("msg_20", NOW.getEpochSecond(), "v1,x", BODY))
        .hasMessageNotContaining(secret.reveal());
  }
}
