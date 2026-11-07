package com.krizaka.sovkora.automationservice.application.service;

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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Receives a signed inbound call to a webhook trigger and starts the run it stands for.
 *
 * <p>The signature is the authentication, so nothing is written and nothing started before it is
 * verified, and every reason to refuse an unauthenticated call is the same {@link
 * Rejection#UNAUTHENTICATED}: a caller learns nothing about which triggers exist (ADR-084 §2, the
 * amendment of ADR-035). Hash and crypto work happens outside any transaction; the only write is
 * one atomic statement.
 */
@Service
public class WebhookIngressService {

  private static final Logger logger = LoggerFactory.getLogger(WebhookIngressService.class);

  private static final Pattern WEBHOOK_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
  private static final String GLOBAL_BUCKET = "hooks:all";

  /** Work done for an unknown trigger, so that it takes as long as for a known one. */
  private static final byte[] DECOY_KEY = new byte[WebhookSecret.KEY_BYTES];

  private final WebhookCredentialStore credentialStore;
  private final WebhookRunLedger ledger;
  private final RunRepository runs;
  private final SecretSealer sealer;
  private final CallLimiter limiter;
  private final RunDispatcher dispatcher;
  private final AutomationSettingsService settings;
  private final Clock clock;
  private final ObjectMapper json;

  public WebhookIngressService(
      WebhookCredentialStore credentialStore,
      WebhookRunLedger ledger,
      RunRepository runs,
      SecretSealer sealer,
      CallLimiter limiter,
      RunDispatcher dispatcher,
      AutomationSettingsService settings,
      Clock clock,
      ObjectMapper json) {
    this.credentialStore =
        Objects.requireNonNull(credentialStore, "WebhookCredentialStore cannot be null");
    this.ledger = Objects.requireNonNull(ledger, "WebhookRunLedger cannot be null");
    this.runs = Objects.requireNonNull(runs, "RunRepository cannot be null");
    this.sealer = Objects.requireNonNull(sealer, "SecretSealer cannot be null");
    this.limiter = Objects.requireNonNull(limiter, "CallLimiter cannot be null");
    this.dispatcher = Objects.requireNonNull(dispatcher, "RunDispatcher cannot be null");
    this.settings = Objects.requireNonNull(settings, "AutomationSettingsService cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
    this.json = Objects.requireNonNull(json, "ObjectMapper cannot be null");
  }

  /**
   * The largest body a call may carry, from the rule set.
   *
   * @return bytes
   */
  public int bodyLimitBytes() {
    return settings.value(SettingKey.WEBHOOK_BODY_MAX_BYTES);
  }

  /**
   * Handles one inbound call.
   *
   * @param rawTriggerId the id in the path, as received
   * @param webhookId the {@code webhook-id} header, or null
   * @param timestamp the {@code webhook-timestamp} header, or null
   * @param signature the {@code webhook-signature} header, or null
   * @param declaredLength the {@code Content-Length}, or -1 when unknown
   * @param body the body, read only if every earlier check passed
   * @return the run recorded for the call
   * @throws WebhookRejectedException when the call is refused, before any work is done for it
   */
  public IngressResult accept(
      String rawTriggerId,
      String webhookId,
      String timestamp,
      String signature,
      long declaredLength,
      BodySource body) {
    int limit = bodyLimitBytes();
    if (declaredLength > limit) {
      throw rejected(Rejection.PAYLOAD_TOO_LARGE, 0, null);
    }
    requireToken(GLOBAL_BUCKET, SettingKey.WEBHOOK_GLOBAL_PER_MINUTE);
    UUID triggerId = parseId(rawTriggerId);
    Optional<TriggerCredentials> known =
        triggerId == null ? Optional.empty() : credentialStore.credentials(triggerId);
    known.ifPresent(
        credentials ->
            requireToken(
                "hooks:pre:" + credentials.triggerId(), SettingKey.WEBHOOK_PREVERIFY_PER_MINUTE));

    byte[] raw = readBody(body, limit);
    boolean headersOk = headersAcceptable(webhookId, timestamp);
    boolean signed = verify(known.orElse(null), signature, webhookId, timestamp, raw);
    if (!headersOk || !signed || known.isEmpty() || !known.get().active()) {
      logger.info("webhook refused: unauthenticated (trigger {})", triggerId);
      throw rejected(Rejection.UNAUTHENTICATED, 0, null);
    }
    TriggerCredentials credentials = known.get();

    Optional<UUID> earlier = ledger.existingWebhookRun(credentials.triggerId(), webhookId);
    if (earlier.isPresent()) {
      throw rejected(Rejection.REPLAYED, 0, earlier.get());
    }
    requireToken("hooks:run:" + credentials.triggerId(), SettingKey.WEBHOOK_RUNS_PER_MINUTE);
    Instant now = clock.instant();
    Instant dayStart =
        now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    if (runs.runsSince(credentials.automationId(), dayStart)
        >= settings.value(SettingKey.MAX_RUNS_PER_DAY)) {
      long untilTomorrow = Duration.between(now, dayStart.plus(Duration.ofDays(1))).toSeconds();
      throw rejected(Rejection.DAILY_BUDGET, Math.max(1, untilTomorrow), null);
    }

    UUID runId = UUID.randomUUID();
    boolean claimed =
        ledger.recordWebhookRun(
            runId,
            credentials.automationId(),
            credentials.triggerId(),
            webhookId,
            keptPayload(raw),
            raw.length);
    if (!claimed) {
      throw rejected(
          Rejection.REPLAYED,
          0,
          ledger.existingWebhookRun(credentials.triggerId(), webhookId).orElse(null));
    }
    dispatcher.submit(runId);
    logger.info("webhook accepted: trigger {} started run {}", credentials.triggerId(), runId);
    return new IngressResult(runId, RunStatus.QUEUED);
  }

  private void requireToken(String bucket, SettingKey perMinute) {
    CallLimiter.Decision decision = limiter.tryAcquire(bucket, settings.value(perMinute));
    if (!decision.allowed()) {
      throw rejected(Rejection.RATE_LIMITED, decision.retryAfterSeconds(), null);
    }
  }

  private byte[] readBody(BodySource body, int limit) {
    try {
      byte[] raw = body.readAtMost(limit);
      if (raw == null) {
        throw rejected(Rejection.PAYLOAD_TOO_LARGE, 0, null);
      }
      return raw;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private boolean headersAcceptable(String webhookId, String timestamp) {
    if (webhookId == null || !WEBHOOK_ID.matcher(webhookId).matches() || timestamp == null) {
      return false;
    }
    try {
      long seconds = Long.parseLong(timestamp.strip());
      long tolerance = settings.value(SettingKey.WEBHOOK_TOLERANCE_SECONDS);
      return Math.abs(clock.instant().getEpochSecond() - seconds) <= tolerance;
    } catch (NumberFormatException notANumber) {
      return false;
    }
  }

  /** Checks the current secret and, during a rotation grace, the previous one — always both. */
  private boolean verify(
      TriggerCredentials credentials,
      String signature,
      String webhookId,
      String timestamp,
      byte[] raw) {
    String idForMac = webhookId == null ? "" : webhookId;
    String timeForMac = timestamp == null ? "" : timestamp;
    if (credentials == null) {
      WebhookSignature.verify(DECOY_KEY, signature, idForMac, timeForMac, raw);
      return false;
    }
    boolean current =
        matches(
            credentials.triggerId(),
            credentials.secretSealed(),
            signature,
            idForMac,
            timeForMac,
            raw);
    boolean previous =
        credentials.previousSealed() != null
            && credentials.previousUntil() != null
            && credentials.previousUntil().isAfter(clock.instant())
            && matches(
                credentials.triggerId(),
                credentials.previousSealed(),
                signature,
                idForMac,
                timeForMac,
                raw);
    return current | previous;
  }

  /**
   * Whether the signature is the one this sealed secret makes. A secret that cannot be opened (a
   * master key this deployment no longer holds) verifies nothing — never a fallback to another key.
   */
  private boolean matches(
      UUID triggerId,
      String sealed,
      String signature,
      String webhookId,
      String timestamp,
      byte[] raw) {
    byte[] key;
    try {
      key = WebhookSecret.of(sealer.open(triggerId, sealed)).key();
    } catch (IllegalStateException | IllegalArgumentException unreadable) {
      logger.error("the secret of trigger {} cannot be opened", triggerId);
      return false;
    }
    return WebhookSignature.verify(key, signature, webhookId, timestamp, raw);
  }

  /** The body as JSON text when it is a JSON object, else nothing: it is data, never a command. */
  private String keptPayload(byte[] raw) {
    if (raw.length == 0) {
      return null;
    }
    try {
      JsonNode node = json.readTree(new String(raw, StandardCharsets.UTF_8));
      return node != null && node.isObject() ? node.toString() : null;
    } catch (RuntimeException notJson) {
      return null;
    }
  }

  private static UUID parseId(String raw) {
    try {
      return raw == null ? null : UUID.fromString(raw);
    } catch (IllegalArgumentException notAnId) {
      return null;
    }
  }

  private static WebhookRejectedException rejected(Rejection why, long retryAfter, UUID runId) {
    return new WebhookRejectedException(why, retryAfter, runId);
  }
}
