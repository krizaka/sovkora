package com.krizaka.sovkora.interceptor.governance;

import com.krizaka.billing.domain.exception.InsufficientCreditsException;
import com.krizaka.billing.domain.model.BillableCapability;
import com.krizaka.billing.domain.model.CreditHoldCommand;
import com.krizaka.billing.domain.model.CreditHoldResponse;
import com.krizaka.billing.domain.model.EntitlementSnapshot;
import com.krizaka.billing.domain.model.UnmeteredTurn;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.billing.domain.port.EntitlementProvider;
import com.krizaka.billing.domain.port.UnmeteredTurnRepository;
import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.jobs.domain.model.JobCommand;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gates the synchronous chat path on what the actor may do and can afford (ADR-033 §6.2).
 *
 * <p>Two questions in order, because they have different answers: <b>entitlement</b> gates access
 * ("your plan does not include video") and <b>credits</b> gate volume ("your plan includes video,
 * you have none left"). Asking them separately is what lets the paywall say which one it is.
 *
 * <p>Streaming chat must not pay a broker round-trip (AGENTS.md §6), so both checks are synchronous
 * — and the hold is the only blocking billing call on the turn. Settlement happens later, off the
 * response path, against the tokens the model actually produced.
 *
 * <p>A refusal is raised as a {@link PipelineShortCircuitException}: the pipeline executor swallows
 * ordinary interceptor failures so a broken enrichment cannot lose a user's turn, and a gate that
 * were merely logged would let every refused request through.
 *
 * <p><b>Failure posture: open, and accounted</b> (ADR-064). When billing cannot be reached the turn
 * is served without a hold — a billing outage must not become a chat outage, the same trade the
 * asynchronous submission path made and wrote down in {@code JobMeteringService}. It was already
 * served free before this was written, by the executor's {@code catch}; the difference is that now
 * somebody chose it, and it leaves a record: {@link UnmeteredTurnRepository} makes the turn durable
 * before it is served, so free inference during an outage can be reconciled afterwards. If that
 * record cannot be made either, the turn is refused — reconcilable free inference is the product,
 * silent free inference is not. Everything else this gate did not anticipate stops the turn: it
 * {@linkplain #failsClosed() fails closed}.
 *
 * <p>The opposite posture to the crisis guard's facing an image it cannot read (ADR-063), and both
 * are chosen: what that guard's failure costs is a person, what this gate's costs is margin.
 */
public class EntitlementInterceptor implements PromptContextInterceptor {

  private static final Logger log = LoggerFactory.getLogger(EntitlementInterceptor.class);

  /** Metadata key the pipeline already carries the actor under (DynamicPipelineExecutor). */
  private static final String USER_ID_KEY = "userId";

  private static final String CONVERSATION_ID_KEY = "conversationId";

  private static final String CHAT_ENTITLEMENT = "capability.chat";
  private static final String REASON_NOT_ENTITLED = "capability_not_in_plan";
  private static final String REASON_NO_CREDITS = "insufficient_credits";

  private final EntitlementProvider entitlementProvider;
  private final CreditAuthorizationClient creditAuthorizationClient;
  private final UnmeteredTurnRepository unmeteredTurns;

  public EntitlementInterceptor(
      EntitlementProvider entitlementProvider,
      CreditAuthorizationClient creditAuthorizationClient,
      UnmeteredTurnRepository unmeteredTurns) {
    this.entitlementProvider =
        Objects.requireNonNull(entitlementProvider, "EntitlementProvider cannot be null");
    this.creditAuthorizationClient =
        Objects.requireNonNull(
            creditAuthorizationClient, "CreditAuthorizationClient cannot be null");
    this.unmeteredTurns =
        Objects.requireNonNull(unmeteredTurns, "UnmeteredTurnRepository cannot be null");
  }

  /** The wire key both {@code job.*} producers stamp; defined once in the Tier-1 contract. */
  private static final String DEFERRED_METERING_KEY = JobCommand.DEFERRED_METERING_KEY;

  @Override
  public PromptContext beforeExecution(PromptContext context) {
    String actorId = actorOf(context);
    if (actorId == null) {
      // No actor means no wallet to charge and no plan to read — an internal or anonymous turn.
      return context;
    }

    EntitlementSnapshot snapshot = entitlementProvider.forActor(actorId);
    if (snapshot.resolved() && !snapshot.allows(CHAT_ENTITLEMENT)) {
      throw new PipelineShortCircuitException(
          getId(),
          REASON_NOT_ENTITLED,
          "Plan " + snapshot.planKey() + " does not include chat",
          null);
    }

    if (meteredElsewhere(context)) {
      // The entitlement check above still ran — whether this actor may chat at all is unchanged.
      // What is skipped is the per-turn HOLD, because the orchestration that dispatched this
      // inference settles it: a Studio run takes one hold for the whole run and settles the sum of
      // its steps (ADR-041). Taking a second hold here billed every step twice — measured at 16
      // credits for 8 inferences, eleven holds where there should have been three (ADR-044).
      //
      // The comment on the hold below says "this turn is synchronous, there is no async job behind
      // it". That was the assumption, and a Studio step is precisely the case it excludes.
      log.debug("Chat turn for actor={} is metered by its orchestration; taking no hold", actorId);
      return context;
    }

    CreditHoldResponse hold;
    try {
      hold =
          creditAuthorizationClient.hold(
              new CreditHoldCommand(
                  actorId,
                  BillableCapability.CHAT,
                  null,
                  correlationOf(context, actorId),
                  // No jobId: this turn is synchronous, there is no async job behind it.
                  null,
                  0L));
    } catch (InsufficientCreditsException refusal) {
      throw new PipelineShortCircuitException(
          getId(), REASON_NO_CREDITS, refusal.getMessage(), refusal);
    } catch (RuntimeException unreachable) {
      // The posture this class declares: open, and accounted. The record is written before the
      // turn is served; if it cannot be, record() throws and the turn stops (failsClosed).
      unmeteredTurns.record(
          new UnmeteredTurn(
              actorId,
              BillableCapability.CHAT,
              correlationOf(context, actorId),
              "billing unreachable: " + unreachable.getClass().getSimpleName(),
              Instant.now()));
      log.warn(
          "Billing unreachable for actor={}; turn served without a hold and recorded as unmetered",
          actorId);
      return context;
    }

    if (!hold.metered()) {
      return context;
    }
    if (hold.dryRun()) {
      log.info("Shadow-metered chat turn for actor={}: hold {}", actorId, hold.holdId());
    }
    return context.withBillingHold(hold.holdId());
  }

  /** Gating is a policy question answered from rows and a balance — it calls no model. */
  @Override
  public boolean isAiDependent() {
    return false;
  }

  /**
   * A control: the billing outage it survives is handled above, and anything else it did not
   * anticipate stops the turn rather than serving it unmetered and unrecorded.
   */
  @Override
  public boolean failsClosed() {
    return true;
  }

  /**
   * Whether something upstream already authorised and will settle this turn.
   *
   * <p>The producer says so explicitly rather than the interceptor inferring it: a marker in the
   * context is a claim its author is accountable for, while sniffing for a {@code runId} would make
   * every future producer that happens to carry one silently unbilled.
   */
  private static boolean meteredElsewhere(PromptContext context) {
    return Boolean.TRUE.equals(context.userMetadata().get(DEFERRED_METERING_KEY))
        || "true".equals(String.valueOf(context.userMetadata().get(DEFERRED_METERING_KEY)));
  }

  private static String actorOf(PromptContext context) {
    Object actor = context.userMetadata().get(USER_ID_KEY);
    return actor instanceof String s && !s.isBlank() ? s : null;
  }

  /**
   * The conversation ties every hold of one exchange together for the §6.5 roll-up; the actor is
   * the fallback so a turn outside a conversation still carries a correlation the ledger accepts.
   */
  private static String correlationOf(PromptContext context, String actorId) {
    Object conversation = context.userMetadata().get(CONVERSATION_ID_KEY);
    return conversation instanceof String s && !s.isBlank() ? s : actorId;
  }
}
