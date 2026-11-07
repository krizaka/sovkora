---
title: "Sovkora — Subscription, credits & metering architecture"
description: "Target architecture for the Billing & Entitlements bounded context: plans, prepaid credit wallets, the hold/settle metering protocol, the DB-driven pricebook, and the Lago + PSP integration seam."
category: Architecture
order: 6
---

# Sovkora — Subscription, credits & metering architecture

> **Scope**: the monetisation plane of Sovkora — how a user is entitled to a capability, how
> consumption of that capability is measured, how it is debited from a credit balance, and how that
> balance is replenished (subscription grant, à-la-carte top-up).
> **Normative contract**: [`AGENTS.md`](../AGENTS.md). Nothing here overrides it.
> **Decision record**: ADR-033 (internal).
> **Status**: design accepted, implementation phased (§16). Phases 0–3 are **100% local** and
> shippable today; phase 4 (payment service provider) is **gated behind the exit from the local
> phase** — AGENTS.md §0 forbids remote secrets and cloud dependencies for now.

---

## 1. The four questions, answered up front

| Question | Answer | Where |
|:---|:---|:---|
| **RabbitMQ or not?** | **Both, on purpose.** Authorisation (may this request run?) is a **synchronous** internal HTTP call on the hot path. Settlement, Lago sync and subscription lifecycle go **through the broker**. Putting authorisation on the broker would violate AGENTS.md §6 ("synchronous = low-latency interactive"). | §9 |
| **Do usable APIs exist?** | Yes. **Lago** (AGPLv3, self-hostable, v1.45.1 — Apr 2026) already implements plans, subscriptions, prepaid **wallets**, usage-event ingestion and PSP connectors. It becomes the **invoicing & money** engine. It does **not** become the authorisation engine. | §11 |
| **How is consumption measured?** | Per-capability **billable units** (1k tokens, image×step-class, output-second, 1k characters, audio-minute), captured where the truth lives — the engine metadata for chat, the worker report for media — then converted to credits by a **versioned, DB-driven pricebook**. | §7 |
| **How is billing performed?** | A **two-phase hold/settle protocol** over an **append-only credit ledger** owned by Sovkora. Estimate is *held* before execution, real consumption *settles* it after, failures *release* it. The ledger authorises; Lago invoices. | §6 |

**The one-sentence architecture**: *Sovkora owns the real-time credit ledger because authorisation
is on the hot path of every request; Lago owns money because invoicing, prorata, dunning and tax are
solved problems we must not rewrite.*

---

## 2. Why this is not "just add a Stripe subscription"

Sovkora does not sell seats. It sells **variable-cost compute** — a 4-second video costs three
orders of magnitude more to produce than a chat reply. Three consequences drive the whole design:

1. **Authorisation must precede execution.** Once an MLX video job starts on the Mac, the cost is
   already sunk. A post-paid model lets a user with a $0 balance burn an hour of GPU. Hence the
   **hold**.
2. **The estimate is never the truth.** A chat turn's token count is unknown before generation; a
   video's real cost depends on the frames actually rendered. Hence the **settle**.
3. **Cost basis is local, not a provider invoice.** With Ollama/MLX on owned hardware there is no
   upstream bill to pass through. The unit of cost is **GPU-seconds on a hardware class**, and the
   pricebook is a *calibrated* artefact, not a markup. Hence §8.

The system must also stay **configurable without deploy**: the brief requires that new subscription
types be addable "once the solution is robust". That is only true if a plan is a **row**, not a Java
enum. §15 makes that build-enforced.

---

## 3. Vocabulary — six concepts, kept strictly separate

Conflating these is the single most common way a credit system rots. Each has one owner and one job.

| Concept | Question it answers | Nature | Changes |
|:---|:---|:---|:---|
| **Plan** | What does the user pay per month? | Recurring commercial offer (`free`, `premium`, `ultimate`, …) | Admin, rarely |
| **Entitlement** | Is this user *allowed* to do this at all? | Boolean or limit (`capability.video=true`, `concurrency.jobs=5`, `model.class=premium`) | With the plan |
| **Credit** | How much *volume* remains? | Fungible prepaid unit in a wallet | Every request |
| **Pricebook** | How many credits does this action cost? | Versioned rate table (capability × model × unit → credits) | Admin, versioned |
| **Package** | Which curated bundle of workflows does a *métier* get? | Entitlement bundle, sellable standalone or with a plan | Product |
| **Rate limit** | How fast may they call? | Requests/minute, concurrent jobs — an **abuse** control, not a **billing** control | Identity context |

**Rate limit stays in `krizaka-users-core`** (`rate_limits`, already implemented). Billing does
not absorb it; billing *drives* it by publishing a subscription-change event that identity consumes to
update `users.rate_limit_tier`. No cross-context FK — SEAM-001 holds.

**Entitlement gates access; credits gate volume.** A `free` user is *not entitled* to video at all
(hard no, no amount of credits helps); a `premium` user *is* entitled but pays credits per second.
This separation is what makes packs métier possible later without touching the credit engine.

---

## 4. Bounded context & service placement

A new context, a new owned database, a new deployable — following exactly the seam doctrine of
ADR-032.

```
krizaka/krizaka-billing/krizaka-billing-api/     Tier-1 · ports + DTOs, zero impl deps
krizaka/krizaka-billing/krizaka-billing-service/  :8095 · owns krizaka_billing_db (role krizaka_billing)
infra/initdb/70-billing.sql                     schema + dev seed, its own database
```

**Why a separate service and not a library in the conversation-service.** Three independent
producers must debit the same wallet — the conversation-service (sync chat), the job-service (async
media), the automation-service (scheduled workflows). A shared ledger reached through three copies
of a persistence library is a distributed monolith with a race condition. One owner, one database,
one write path.

**Why not fold it into identity.** Identity answers *who*; billing answers *how much left*. They
have different change rates, different audit requirements (financial records are retained under
different rules than credentials), and billing must be able to go read-only during an incident
without taking authentication down with it.

### Call graph

```
                         ┌─────────────────────────┐
   client ── edge :8088 ─┤ conversation-svc :8080  │
                         │ job-service      :8090  │──┐  sync HTTP (M2M JWT)
                         │ automation-svc          │  │  POST /credits/holds      ← HOT PATH
                         └─────────────────────────┘  │  POST /credits/{id}/settle
                                                      ▼
                                         ┌──────────────────────────┐
                                         │ billing-service   :8095  │
                                         │  credit ledger (SoT)     │
                                         │  pricebook · entitlements│
                                         └────────┬─────────────────┘
                                                  │ outbox → orazaka.events
                                                  │ evt.usage.recorded
                                                  │ evt.subscription.*
                                                  ▼
                                    ┌───────────────────────────────┐
                                    │ Lago (docker-compose, AGPLv3) │  invoices · plans · dunning
                                    │        └── PSP (deferred §16) │  cards · SEPA
                                    └───────────────────────────────┘
```

The hot path never touches Lago. If Lago is down, Sovkora keeps authorising and metering; events
queue in the outbox and drain on recovery. **This is the availability property that justifies the
local ledger.**

---

## 5. Data model — `infra/initdb/70-billing.sql`

Own database, own role, opaque `ActorId`, no cross-context FK (SEAM-001), own `processed_messages`
copy (contract-copy doctrine, AGENTS.md §6).

### 5.1 Commercial plane

```sql
-- A plan is a ROW. Adding "ultimate+" or "studio" is an admin action, never a deploy.
CREATE TABLE billing_plan (
    plan_key            VARCHAR(50)  PRIMARY KEY,      -- free | premium | ultimate | …
    label               VARCHAR(100) NOT NULL,
    tier_rank           INT          NOT NULL,          -- ordering for upgrade/downgrade logic
    monthly_credit_grant BIGINT      NOT NULL DEFAULT 0,-- credits granted at each period start
    price_cents         INT          NOT NULL DEFAULT 0,
    currency            CHAR(3)      NOT NULL,          -- ISO 4217; billing.currency (CAD) when a plan names none
    rate_limit_tier_key VARCHAR(50)  NOT NULL,          -- OPAQUE ref into identity — no FK
    external_plan_code  VARCHAR(100),                   -- Lago plan code; NULL while local-only
    is_public           BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Entitlement = typed key/value. New entitlement keys need no schema change.
CREATE TABLE billing_plan_entitlement (
    plan_key        VARCHAR(50)  NOT NULL REFERENCES billing_plan(plan_key) ON DELETE CASCADE,
    entitlement_key VARCHAR(120) NOT NULL,   -- capability.video · concurrency.jobs · model.class · package.marketing
    value_type      VARCHAR(20)  NOT NULL,   -- boolean | int | string
    value           VARCHAR(255) NOT NULL,
    PRIMARY KEY (plan_key, entitlement_key)
);

-- Packs métier: the PRICE TAG, and nothing else. Same entitlement grammar as a plan.
-- `label` and `profession` used to sit here; they moved to the catalogue in the studio
-- context (`pack` / `pack_i18n`, ADR-036) so this service stopped being a CMS. pack_key is
-- an opaque string shared with that table, with no FK between the two databases.
CREATE TABLE billing_pack (
    pack_key           VARCHAR(50)  PRIMARY KEY,   -- OPAQUE, mirrored in studio's `pack` — no FK
    price_cents        INT          NOT NULL DEFAULT 0,
    included_credits   BIGINT       NOT NULL DEFAULT 0,
    external_plan_code VARCHAR(100),
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE TABLE billing_pack_entitlement (
    pack_key     VARCHAR(50)  NOT NULL REFERENCES billing_pack(pack_key) ON DELETE CASCADE,
    entitlement_key VARCHAR(120) NOT NULL,
    value_type      VARCHAR(20)  NOT NULL,
    value           VARCHAR(255) NOT NULL,
    PRIMARY KEY (pack_key, entitlement_key)
);

CREATE TABLE billing_subscription (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id              VARCHAR(255) NOT NULL,   -- OPAQUE — no FK into identity
    plan_key              VARCHAR(50)  NOT NULL REFERENCES billing_plan(plan_key),
    status                VARCHAR(30)  NOT NULL,   -- TRIALING|ACTIVE|PAST_DUE|CANCELED|EXPIRED
    period_start          TIMESTAMPTZ  NOT NULL,
    period_end            TIMESTAMPTZ  NOT NULL,
    cancel_at_period_end  BOOLEAN      NOT NULL DEFAULT FALSE,
    external_subscription_id VARCHAR(255),         -- Lago; NULL while local-only
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_billing_subscription_active
    ON billing_subscription(actor_id) WHERE status IN ('TRIALING','ACTIVE','PAST_DUE');
```

The partial unique index is the invariant "**one active subscription per actor**" enforced by the
database, not by a read-before-write in Java (ERR-109).

### 5.2 Credit plane — the ledger is the source of truth

```sql
-- Materialised balance. Two buckets with DIFFERENT expiry semantics (§6.4).
CREATE TABLE credit_wallet (
    actor_id          VARCHAR(255) PRIMARY KEY,
    balance_granted   BIGINT      NOT NULL DEFAULT 0,  -- from the subscription; expires at period end
    balance_purchased BIGINT      NOT NULL DEFAULT 0,  -- bought à la carte; long/never expiry
    held              BIGINT      NOT NULL DEFAULT 0,  -- sum of ACTIVE holds — denormalised for O(1) authz
    version           BIGINT      NOT NULL DEFAULT 0,  -- optimistic lock
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_wallet_non_negative
        CHECK (balance_granted >= 0 AND balance_purchased >= 0 AND held >= 0)
);

-- APPEND-ONLY. No UPDATE, no DELETE, ever. This is the financial record.
CREATE TABLE credit_ledger_entry (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id         VARCHAR(255) NOT NULL,
    entry_type       VARCHAR(20)  NOT NULL,  -- GRANT|PURCHASE|DEBIT|REFUND|EXPIRY|ADJUSTMENT
    bucket           VARCHAR(20)  NOT NULL,  -- GRANTED|PURCHASED
    amount           BIGINT       NOT NULL,  -- signed: +credit, -debit
    balance_after    BIGINT       NOT NULL,  -- bucket balance after this entry — audit anchor
    reference_type   VARCHAR(50),            -- HOLD|JOB|SUBSCRIPTION|TOPUP|ADMIN
    reference_id     VARCHAR(255),
    idempotency_key  VARCHAR(255) NOT NULL,  -- AMQP messageId, hold id, PSP event id…
    reason           VARCHAR(255),
    created_by       VARCHAR(255) NOT NULL,  -- actor or 'system' — every ADJUSTMENT is attributable
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_ledger_idempotency ON credit_ledger_entry(idempotency_key);
CREATE INDEX idx_ledger_actor_time ON credit_ledger_entry(actor_id, created_at DESC);
```

`idempotency_key UNIQUE` is the whole at-least-once story: a redelivered settlement message inserts
a duplicate key, the insert fails with `DataIntegrityViolationException`, the consumer treats that as
"already applied" and acks. No read-before-write, per ERR-109.

`balance_after` makes the ledger self-verifying: a reconciliation job replays entries and asserts
each `balance_after` matches the running sum. Drift is detected, not discovered by a customer.

Append-only is enforced by a **trigger**, not by `REVOKE` — the table owner keeps implicit rights on
its own table, so a grant-based rule is not actually a constraint:

```sql
CREATE OR REPLACE FUNCTION credit_ledger_immutable() RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'credit_ledger_entry is append-only (ADR-033) — % rejected', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_credit_ledger_immutable
  BEFORE UPDATE OR DELETE ON credit_ledger_entry
  FOR EACH ROW EXECUTE FUNCTION credit_ledger_immutable();
```

Consequence for JPA: the entity must be **insert-only** — no `@Version`, no dirty-checking path. A
stray `merge()` now fails loudly at the database instead of silently rewriting a financial record.

```sql
-- The reservation. Its TTL is what prevents leaked credits when a worker dies.
CREATE TABLE credit_hold (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id           VARCHAR(255) NOT NULL,
    estimated_credits  BIGINT       NOT NULL,
    settled_credits    BIGINT,
    status             VARCHAR(20)  NOT NULL,  -- ACTIVE|SETTLED|RELEASED|EXPIRED
    capability         VARCHAR(50)  NOT NULL,  -- CHAT|IMAGE|AUDIO|VIDEO|AGENT (mirrors business.api.Capability, contract-copied)
    model_name         VARCHAR(255),
    pricebook_version  INT          NOT NULL,  -- pin the rate: a mid-flight price change cannot corrupt settlement
    correlation_id     VARCHAR(255) NOT NULL,  -- Intention.id — ties a hold to its whole agent run
    job_id             VARCHAR(36),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ  NOT NULL,
    settled_at         TIMESTAMPTZ
);
CREATE INDEX idx_hold_sweeper ON credit_hold(expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_hold_job ON credit_hold(job_id) WHERE job_id IS NOT NULL;
```

### 5.3 Pricing plane — versioned, DB-driven (ADR-027/031 pattern)

```sql
-- Versioned rate table. A row is NEVER updated in place: a price change closes the current row
-- (effective_to) and inserts a new version. Holds pin the version they were priced with.
CREATE TABLE credit_pricebook (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version           INT          NOT NULL,
    capability        VARCHAR(50)  NOT NULL,
    model_name        VARCHAR(255),             -- NULL = default rate for the capability
    unit              VARCHAR(30)  NOT NULL,    -- KILOTOKEN|IMAGE_STEP|OUTPUT_SECOND|KILOCHAR|AUDIO_MINUTE|GPU_SECOND|CALL
    credits_per_unit  NUMERIC(12,4) NOT NULL,
    minimum_credits   BIGINT       NOT NULL DEFAULT 1,   -- floor: no free ride on tiny requests
    estimate_credits  BIGINT       NOT NULL,             -- what the HOLD reserves before execution
    effective_from    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    effective_to      TIMESTAMPTZ,
    CONSTRAINT ck_pricebook_positive CHECK (credits_per_unit >= 0)
);
-- ⚠ `unit` is deliberately NOT in this key — one current rate per capability × model. That makes
-- AUDIO's two default rates (TTS KILOCHAR, STT AUDIO_MINUTE) unrepresentable; see §8's open
-- conflict 1, which must be resolved before phase 1.
CREATE UNIQUE INDEX idx_pricebook_current
    ON credit_pricebook(capability, COALESCE(model_name, '*')) WHERE effective_to IS NULL;

-- The metering fact table: one row per measured consumption. Feeds Lago, analytics and disputes.
CREATE TABLE usage_event (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id         VARCHAR(255) NOT NULL,
    capability       VARCHAR(50)  NOT NULL,
    model_name       VARCHAR(255),
    unit             VARCHAR(30)  NOT NULL,
    quantity         NUMERIC(14,4) NOT NULL,
    credits_charged  BIGINT       NOT NULL,
    hold_id          UUID,
    job_id           VARCHAR(36),
    correlation_id   VARCHAR(255) NOT NULL,
    occurred_at      TIMESTAMPTZ  NOT NULL,
    external_synced_at TIMESTAMPTZ                 -- NULL until Lago acknowledged it
);
CREATE INDEX idx_usage_unsynced ON usage_event(occurred_at) WHERE external_synced_at IS NULL;
CREATE INDEX idx_usage_actor_time ON usage_event(actor_id, occurred_at DESC);
```

### 5.4 Audit & plumbing

```sql
-- Same shape as 60-governance's policy_version_history: every plan/pricebook mutation is
-- snapshotted and attributable. Price changes are a legal artefact, not a config tweak.
CREATE TABLE billing_version_history (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type  VARCHAR(50)  NOT NULL,   -- PLAN|PACKAGE|PRICEBOOK
    entity_key   VARCHAR(120) NOT NULL,
    snapshot     JSONB        NOT NULL,
    sha256_hash  VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(255) NOT NULL
);

CREATE TABLE billing_outbox (   -- identical contract to identity_outbox
    id UUID PRIMARY KEY, aggregate_type VARCHAR(100) NOT NULL, aggregate_id VARCHAR(255) NOT NULL,
    exchange VARCHAR(100) NOT NULL, routing_key VARCHAR(255) NOT NULL, message_id UUID NOT NULL UNIQUE,
    payload JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ,
    attempts INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE processed_messages (   -- this service's own copy
    consumer VARCHAR(100) NOT NULL, message_id VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ DEFAULT now(), PRIMARY KEY (consumer, message_id)
);
```

---

## 6. The metering protocol — hold → settle → release

This is the heart of the system. Three verbs, one invariant: **no compute starts without a hold, and
no hold survives its TTL**.

### 6.1 Authorisation (`POST /api/v1/credits/holds`)

```
available = balance_granted + balance_purchased − held
```

Single statement, single transaction, no read-before-write:

```sql
UPDATE credit_wallet
   SET held = held + :estimate, version = version + 1, updated_at = now()
 WHERE actor_id = :actorId
   AND balance_granted + balance_purchased - held >= :estimate;
-- 0 rows updated ⇒ insufficient credits ⇒ 402
```

The `CHECK` constraint and the conditional `UPDATE` make over-drafting **impossible at the database
level**, regardless of concurrency. Two simultaneous video submissions cannot both pass on the same
last 100 credits.

**Response on refusal** is structured, never a bare 402: remaining balance, credits required, the
capability, and the two remedies (upgrade plan / top up). The UI needs all four to render a useful
paywall instead of an error.

### 6.2 Synchronous path — interactive chat & code

Streaming chat must not pay a broker round-trip (AGENTS.md §6). The gate is a
**`PromptContextInterceptor`** — the pipeline already supports short-circuiting, which is exactly the
semantics needed.

```
EntitlementInterceptor  (governance/, order ~4)
   ├─ entitlement check from a Redis-cached snapshot (TTL 60s, invalidated by evt.subscription.*)
   │     └─ not entitled → SHORT-CIRCUIT → 403 capability_not_in_plan
   └─ hold (sync HTTP, ~2ms p99 local) with pricebook.estimate_credits
         └─ insufficient → SHORT-CIRCUIT → 402 insufficient_credits

   … inference runs …

EngineStreamBridge / AbstractEngine
   └─ on completion: read token usage from InternalChatResponse.metadata
         └─ settle(holdId, KILOTOKEN, actualTokens)   ← fire-and-forget, off the response path
```

**Two hard rules on the sync path.** (a) The hold call is the *only* blocking billing call; settle is
asynchronous and must never delay the last token to the user. (b) If the billing service is
unreachable, behaviour is governed by `billing.fail-mode` in `billing_runtime_config` (§13.5):
`FAIL_CLOSED` (refuse — protects margin) or `FAIL_OPEN` (allow + record a reconciliation debt —
protects UX). Ship `FAIL_OPEN` for chat, `FAIL_CLOSED` for video. Making this a runtime config key
rather than a hardcoded choice is deliberate: it is the lever you pull during an incident.

### 6.3 Asynchronous path — image, audio, video, workflows

```
job-service                                  billing-service                worker (Python/MLX)
   │                                              │                                │
   ├─ POST /credits/holds ───────────────────────►│                                │
   │◄── 201 {holdId, estimate} ───────────────────┤                                │
   │                                              │                                │
   ├─ publish orazaka.jobs · job.video.generate ──┼───────────────────────────────►│
   │     payload carries holdId + correlationId   │                                │
   │                                              │        … MLX renders …         │
   │                                              │◄── job.{id}.done ──────────────┤
   │                                              │    {frames, fps, gpuSeconds}   │
   │                                    settle(hold, OUTPUT_SECOND, qty)           │
   │                                    → DEBIT ledger entry                       │
   │                                    → usage_event row                          │
   │                                    → outbox: evt.usage.recorded               │
```

* `job.{id}.error` → **release** the hold, zero debit. A failed generation is never billed. This is
  a product promise, and it is why the hold (not a pre-debit) is the right primitive.
* Idempotency by AMQP `messageId` into `processed_messages` + the ledger's `idempotency_key`. Two
  independent guards, because a financial double-debit is unrecoverable trust damage.
* **Hold sweeper** — a scheduled task releases `ACTIVE` holds past `expires_at` (TTL = job timeout ×
  2). Without it, a worker crash silently freezes a user's balance forever. This is the single most
  commonly forgotten component in credit systems.

### 6.4 Consumption order & expiry

Debit **granted first, purchased second**. Granted credits expire at `period_end` (use-it-or-lose-it,
the standard SaaS mechanic that protects revenue recognition); purchased credits are the user's
property and must have a long or no expiry — in several jurisdictions prepaid balances sold for money
carry consumer-protection constraints that granted promotional credits do not. Mixing the two buckets
into one number makes that distinction unrepresentable, which is why `credit_wallet` has two columns
and never one.

Expiry runs as a scheduled `EXPIRY` ledger entry at period rollover, immediately followed by the new
period's `GRANT`. Both are ledger entries — the user can see exactly what happened.

### 6.5 Agent & workflow runs — roll-up by correlation

An agent loop (plan → act → observe) or a multi-step workflow produces *n* inferences and *m* tool
calls. Each takes its own hold, all sharing `correlation_id = Intention.id`. The UI shows one line
("Workflow 'Video ad' — 340 credits") by aggregating on `correlation_id`, while the ledger keeps the
per-step truth for disputes and cost analysis. Add a flat `CALL`-unit orchestration fee per workflow
run so that automation itself is monetisable independently of the models it invokes — that is the
lever for the future workflow marketplace (§14).

---

## 7. How consumption is measured, per capability

The rule: **measure where the truth already exists**. Do not re-derive on the billing side what the
engine or worker already knows.

| Capability | Billable unit | Source of truth | Available today? |
|:---|:---|:---|:---|
| `CHAT` (incl. code generation) | `KILOTOKEN` (prompt + completion, weighted) | `InternalChatResponse.metadata` (Spring AI usage) | ✅ metadata map exists — needs a typed `TokenUsage` record |
| `IMAGE` (gen) | `IMAGE_STEP` = images × steps × (width × height ÷ 10⁶) — a **megapixel-step** | media worker report (`steps`, `width`, `height`) | ⚠️ worker must add the fields to `job.done` |
| `VIDEO` | `OUTPUT_SECOND` (× model class) | worker report (`frames`, `fps`) — `orazaka_models.recommended_fps` gives the conversion | ⚠️ same |
| `AUDIO` (TTS) | `KILOCHAR` of input text | request payload, known **before** execution ⇒ exact hold, no settle drift | ✅ |
| `AUDIO` (STT) | `AUDIO_MINUTE` of source | asset duration, known before execution | ✅ |
| ↳ *these two are priced **per model**, not per capability* | — | TTS = `orazaka_models` category `speech`; STT = category `audio` | see §8.1 |
| `IMAGE`/`VISION` (analysis) | `CALL` + `KILOTOKEN` of the response | engine metadata | ✅ |
| `AGENT` | roll-up of children + orchestration `CALL` | `correlation_id` aggregation | design |
| Automation/workflow | steps + orchestration `CALL` | `automation_job_execution_log.duration_ms` already recorded | ✅ |
| **Any local model** | `GPU_SECOND` (fallback / calibration basis) | worker wall-clock | ⚠️ worker must report |

**The one change required across the estate**: every executor must return its consumption metrics in
the `job.{id}.done` payload. That is an additive field on an existing contract — no breaking change,
guarded by the existing `AmqpContractIT` fixtures.

**Failure posture of the credit gates** (ADR-064). A gate that cannot reach billing serves the work
**open, and accounted**: before the chat turn or the submission proceeds, the host writes an
`UnmeteredTurn` to its own outbox, and billing collects it into `unmetered_turn` for reconciliation —
billed after the fact or written off by a person, never debited automatically. If the record cannot be
written, the work is refused. A billing outage is not a service outage, and it is not silent free
inference either.

**Assumptions, not invariants.** A quantity measured on the work is an invariant: it is right however
the executor was configured. Two inputs here are not measured, and each is correct only while
something outside the bill stays true.

- **`steps` (`IMAGE_STEP`).** No produced image carries its denoising step count — sd-server takes it
  at launch — so the meter reads `orazaka.core.image.generation.steps` (`IMAGE_GEN_STEPS`), the same
  variable `orazaka start` passes as `--steps` (ADR-062 §5). The quantity is correct only while that
  configuration describes what the executor actually ran. A server started by hand with other steps,
  or a launcher that stops reading the variable, bills the wrong compute and nothing notices.
  `ImageStepsBindingTest` pins that the configuration binds; nothing pins that the server obeyed it.
- **`fps` (`OUTPUT_SECOND`, video generation).** The worker counts the frames it rendered but reports
  the rate it *passed* to the exporter, not the rate read back from the file. It is correct only while
  the exporter honours that argument. Composition no longer assumes it: its frames are counted and its
  rate read on the written file (ADR-063 §7).

**Not true today** (audit #22): the two `AUDIO` rows above are marked ✅, but neither
`SpeechSynthesisStrategy` nor `AudioAnalysisStrategy` reports `characters` or `audioSeconds`. Their
holds settle to nothing and are released — audio has not been billed.

### Why `GPU_SECOND` matters even though users never see it

Users are billed in the friendly unit (an image, a second of video). But the pricebook must be
*calibrated* against real cost, and on owned hardware the only honest cost signal is occupancy of the
Mac's GPU. Recording `gpuSeconds` on every media job gives you, after two weeks of shadow metering
(§16 phase 0), a measured `credits_per_unit` per model instead of a guessed one — and a margin
dashboard per capability. **Do not skip the shadow phase.** Pricing a video model by intuition is how
a plan gets sold at a loss.

---

## 8. What is one credit worth?

Fix this **once**, write it down, never silently redefine it — a credit whose meaning drifts destroys
the user's ability to reason about their balance and makes historical ledger entries meaningless.

**Proposed definition**: `1 credit = 1,000 tokens on the default local chat model`, the cheapest
meaningful unit of work in the system. Everything else is expressed relative to it.

`estimate` is what the **hold** reserves before execution (`credit_pricebook.estimate_credits`, NOT
NULL); the settled price is what the measured quantity costs afterwards. They are different numbers
and both must be stated — a table with only settled prices cannot be seeded.

| Action | Unit | Settled credits | Ratio | Hold estimate |
|:---|:---|---:|:---|---:|
| Chat, default local model | 1k tokens | 1 | ×1 | 2 |
| Chat, premium/cloud model | 1k tokens | 8 | ×8 | 16 |
| Image, 512², 20 steps | 5.24 megapixel-steps | 10 | ×10 | — |
| Image, 1024², 40 steps | 41.94 megapixel-steps | **80** | ×80 | — |
| Image, worker default (1024×576, 16 steps) | 9.44 megapixel-steps | 18 | ×18 | 20 |
| TTS (per `speech` model) | 1k characters | 4 | ×4 | 4 |
| STT (per `audio` model) | 1 audio minute | 6 | ×6 | 6 |
| Video (AnimateDiff-Lightning) | 1 output second | 90 | ×90 | 360 (4 s) |
| Workflow orchestration | 1 run | 5 | ×5 | 5 |

The three-order-of-magnitude spread between chat and video is exactly why credits exist rather than
"messages per month". **These numbers are placeholders to be replaced by phase-0 measurements** —
they are shaped correctly (the ratios reflect real relative GPU cost) but the absolute calibration
must come from your hardware.

**The 1024² row was 35 in an earlier draft and is now 80.** That was not a rounding change: at the
512² anchor of 10 credits, `credits_per_unit = 1.9073`, and 1024²/40 steps is **8×** the compute
(4× the pixels × 2× the steps), so 35 under-priced it by ~2.3×. A rate card that charges 3.5× for 8×
the work loses money on exactly the requests that cost the most — the failure `DRY_RUN` exists to
catch. Note also that the worker's real default is **1024×576 @ 16 steps**, not 512² — the 512² row
is a pricing anchor, not a shape the system actually produces, which is why the hold estimate is
sized on the default row instead.

### 8.1 Two pricing-key decisions (resolved)

Both follow one rule: **the pricebook key is what the caller knows *before* the request runs —
`(capability, model)`. Anything that varies *per request* goes into the quantity, never into the
key.** `credit_pricebook`'s uniqueness stays `(capability, COALESCE(model_name,'*'))`.

1. **AUDIO is priced per model, with no capability default.** Its two halves are metered in
   different units, and the config plane already separates them: TTS models are `orazaka_models`
   category `speech` (`piper-*`, `tts-1`), STT models are category `audio` (`whisper-*`), reached by
   different endpoints. The metering unit is a **property of the model** — Whisper bills minutes,
   Piper bills characters, and no model bills both — so it is an *output* of the lookup, not a key
   column. Adding `unit` to the unique index would permit a row that cannot exist while leaving a
   bare `(AUDIO, NULL)` lookup ambiguous at the one moment resolution matters. An unpriced
   `(capability, model)` therefore resolves to **no row**: logged under `DRY_RUN`, refused under
   `ENFORCING`. Never guess a price for compute.
2. **Resolution is folded into the IMAGE unit, not into a row.** The media worker reads `width`/
   `height` from the request payload, so resolution varies per request for the same model; a row per
   model × resolution would be combinatorial and would break the key. Hence the megapixel-step of §7.

Anchor to money in `billing_runtime_config` — one key, one place, admin-editable (full key list and
placement rationale in §13.5):

```
billing.currency                 = CAD        -- ISO 4217 of every amount stated without one
billing.credit.unit-price-cents  = 1          -- 1 credit ≈ 0,01 $ CA at list price
billing.enforcement.mode         = DRY_RUN    -- OFF | DRY_RUN | ENFORCING  (§13.3)
```

> **Currency.** Sovkora bills in **Canadian dollars** — Krizaka is a Montréal company. The billing block
> stays multi-currency: a plan carries its own ISO 4217 code, and `billing.currency` (default `CAD`) is
> the currency of everything stated without one (pack prices, the money anchor). Screens format money
> with `@krizaka/intl` `formatMoney` in the reader's *language* (`en` → "CA$29.00", `fr` → "29,00 $CA"),
> so the currency is always named for an international reader.
>
> **Since ADR-047** the credit is ten times finer: the anchor is `billing.credit.unit-price-millicents
> = 100` (1 credit = 0.1 ¢ CA) and the grants below are ×10 in v2 credits. The prices do not move.

Indicative plan shape (all editable in admin, none in code). `rate_limit_tier_key` is NOT NULL on
`billing_plan` and is an **opaque** reference into identity's `rate_limits` — it must be
stated here or the table cannot be seeded. `ultimate` maps to `enterprise` rather than `admin`
because `admin` is a *role* tier, not a purchasable one, and `enterprise` already carries the 20
concurrent jobs this plan sells.

| Plan | Price | Monthly grant | `rate_limit_tier_key` | Entitlements | Top-up |
|:---|---:|---:|:---|:---|:---|
| `free` | 0 $ CA | 500 | `free` | chat + image only · 1 concurrent job · local models | ❌ |
| `premium` | 29 $ CA | 5,000 | `premium` | + audio, video, agent · 5 jobs · premium models | ✅ |
| `ultimate` | 79 $ CA | 20,000 | `enterprise` | + workflows, automation, API keys · 20 jobs · all models | ✅ |

Prices were €19 / €49 until 2026-10; the CAD prices are the euro ones at ~1.55 CAD/EUR, rounded to the
x9 price points (`infra/migrations/2026-10-11-billing-currency-cad.sql` moves an existing database).

Blocking top-up on `free` is deliberate: an unpaid account able to buy compute is a fraud and abuse
surface (stolen cards, resale of your GPU). Top-up is a privilege of a verified paying relationship.

### 8.2 The entitlement key vocabulary

The keys below are the **contract**, not an illustration: §15 bans plan-key literals in Java, so
these strings are what the admin console writes and what `EntitlementService` reads. Naming them
here is what keeps a new plan an admin action. Eleven keys per plan today.

| Key | Type | `free` | `premium` | `ultimate` |
|:---|:---|:---|:---|:---|
| `capability.chat` | boolean | `true` | `true` | `true` |
| `capability.image` | boolean | `true` | `true` | `true` |
| `capability.audio` | boolean | `false` | `true` | `true` |
| `capability.video` | boolean | `false` | `true` | `true` |
| `capability.agent` | boolean | `false` | `true` | `true` |
| `capability.workflow` | boolean | `false` | `false` | `true` |
| `capability.automation` | boolean | `false` | `false` | `true` |
| `capability.api-keys` | boolean | `false` | `false` | `true` |
| `concurrency.jobs` | int | `1` | `5` | `20` |
| `model.class` | string | `local` | `premium` | `all` |
| `topup.enabled` | boolean | `false` | `true` | `true` |

`topup.enabled` carries the "Top-up" column above as data rather than as code — the `free`-tier block
is a product rule, and a rule that lives in an `if` is a rule that needs a deploy to relax.

An absent key is a **denial** (`EntitlementSnapshot.allows` returns false), so a plan that forgets to
mention a capability fails closed rather than accidentally unlocking it.

---

## 9. RabbitMQ — where, and where not

Direct answer to the brief. **The broker is used for three flows and forbidden for a fourth.**

| Flow | Transport | Why |
|:---|:---|:---|
| **Authorisation (hold)** | ❌ **Synchronous HTTP** (`@HttpExchange`, M2M JWT) | It is a blocking precondition of an interactive request. AGENTS.md §6: *"synchronous = low-latency interactive"*. A request/reply over AMQP here adds a queue hop to every chat turn and turns a broker hiccup into a total outage. |
| **Settlement** (`job.{id}.done\|error` → settle) | ✅ `orazaka.jobs` (existing) | Already event-driven, already idempotent, already DLQ'd. Reuses the contract unchanged. |
| **Lago synchronisation** (`evt.usage.recorded`) | ✅ `orazaka.events` via **transactional outbox** | External system, must never be on a user's critical path, must survive Lago downtime. Textbook outbox case. |
| **Subscription lifecycle** (`evt.subscription.activated\|renewed\|canceled\|payment_failed`) | ✅ `orazaka.events` | Fan-out to ≥ 3 consumers: identity (rate-limit tier), billing (wallet grant), conversation (entitlement cache eviction). Point-to-point HTTP here would hardcode the topology. |

Routing keys, consistent with §6 of the contract:

```
orazaka.events   evt.usage.recorded
                 evt.credit.debited · evt.credit.granted · evt.credit.exhausted
                 evt.subscription.activated|renewed|canceled|payment_failed
                 evt.wallet.low-balance          → notification service / UI nudge
```

`evt.credit.exhausted` and `evt.wallet.low-balance` are the growth hooks: they are what drives the
"you have 8% of your credits left" banner and the upgrade prompt, without the UI polling a balance.

**Entitlement cache.** Redis, key `entitlements:{actorId}`, TTL 60s, evicted on
`evt.subscription.*`. Without it, every chat turn is an extra service hop. With it, entitlement
checks are in-process after the first call. The TTL is the bounded staleness you accept: a downgrade
takes at most 60 seconds to bite, which is correct for a downgrade and irrelevant for an upgrade
(upgrades evict immediately).

---

## 10. Ports & adapters — naming-compliant contracts

Tier-1 `krizaka-billing-api`. Self-validating records (ERR-106/116), one type per file (ERR-103),
outbound ports as `<Capability>Client` / `<Thing>Provider`.

```java
// Inbound port used by every producer on the hot path.
public interface CreditAuthorizationClient {
  CreditHoldResponse hold(CreditHoldCommand command);      // throws InsufficientCreditsException
  void settle(SettleCreditCommand command);
  void release(String holdId, String reason);
}

// Read-side, cached, used by the interceptor.
public interface EntitlementProvider {
  EntitlementSnapshot forActor(String actorId);            // plan + entitlements + balance summary
}

public record CreditHoldCommand(
    String actorId, BillableCapability capability, String modelName,
    String correlationId, String jobId, long estimatedCredits) {
  public CreditHoldCommand {
    Objects.requireNonNull(actorId, "actorId must not be null");
    Objects.requireNonNull(capability, "capability must not be null");
    if (estimatedCredits < 0) throw new IllegalArgumentException("estimatedCredits must be >= 0");
  }
}

public record SettleCreditCommand(
    String holdId, BillableUnit unit, BigDecimal quantity, String idempotencyKey) { … }

public enum BillableUnit { KILOTOKEN, IMAGE_STEP, OUTPUT_SECOND, KILOCHAR, AUDIO_MINUTE, GPU_SECOND, CALL }

// Contract-copy of business.api.Capability (AGENTS.md §6): a Tier-1 contract has ZERO impl deps
// and orazaka-business is Tier-3. The billing contract must NOT import the business enum — it
// declares its own and pins the shape with a fixture, exactly like the AMQP contract copies.
public enum BillableCapability { CHAT, IMAGE, AUDIO, VIDEO, AGENT }
```

Component placement inside `krizaka-billing-service` (ERR-128/129/130):

| Class | Package | Note |
|:---|:---|:---|
| `PlanController`, `PackController`, `SubscriptionController`, `WalletController`, `PricebookController`, `ConfigurationController`, `UsageController` | `infrastructure/adapter/rest` | **Resource-oriented.** No `AdminPlanController` — admin access is `@PreAuthorize("hasRole('ADMIN')")`, not a class name (ERR-128). One `@RequestMapping("/api/v1/billing/<resource>")` base each. |
| `CreditLedgerService`, `EntitlementService`, `SubscriptionService`, `PricingService`, `BillingConfigurationService` | `application/service` | **Capability-oriented.** No `*Manager`, `*Orchestrator`, `*Processor` (ERR-129) |
| `JobSettlementListener`, `SubscriptionEventListener` | `infrastructure/adapter/amqp` | consumer-side idempotency via `processed_messages` |
| `LagoBillingAdapter` | `infrastructure/adapter/rest` | `@HttpExchange` — **no Java SDK exists for Lago**, and AGENTS.md §4 mandates `RestClient`/`@HttpExchange` anyway |
| `EntitlementInterceptor` | `orazaka-interceptors/governance/` | the sync gate; short-circuits (§6.2) |
| `BillingProperties`, `PricebookProperties` | `infrastructure/config` | typed `@ConfigurationProperties`, infra wiring only |

The consuming services depend on `krizaka-billing-api` (Tier-1) — **never** on the billing service's
own domain (Tier-3). SEAM-002 holds.

---

## 11. The external billing engine — Lago, and why not the others

### What Lago brings that we must not rewrite

Lago (AGPLv3, self-hostable via Docker, v1.45.1 — April 2026, ~9.5k GitHub stars, used by Mistral AI
and Groq for exactly this workload) ships: plans & subscriptions with prorata, **prepaid wallets and
credit primitives**, high-throughput usage-event ingestion, invoice generation, dunning, tax-engine
plug-ins, and PSP connectors (Stripe, GoCardless, Adyen). Every one of those is months of work with
a long tail of edge cases (mid-cycle upgrades, refunds, VAT per jurisdiction, failed-payment
retries) that carry legal exposure when wrong.

**Division of responsibility — this is the crux, and it is not the obvious one:**

| Concern | Owner | Why |
|:---|:---|:---|
| Real-time authorisation, hold/settle, ledger | **Sovkora** | Hot path. Cannot depend on an external service's latency or uptime. |
| Plan catalogue, prorata, invoices, dunning, tax | **Lago** | Solved, regulated, boring. Not a differentiator. |
| Cards, SEPA, 3DS, PCI scope | **PSP** (via Lago connector) | Never touch card data. |
| Usage history for invoicing | **Both** — Sovkora writes, Lago aggregates | Reconciled nightly (§17) |

Running Lago **in `docker-compose`** is fully compatible with AGENTS.md §0: it is stateful infra in
Docker, which the contract explicitly allows. The **PSP** is the part that is not — it needs remote
secrets and a public webhook endpoint. Hence phase 4 is gated (§16).

### API landscape, verified July 2026

| Option | Licence | Fit | Verdict |
|:---|:---|:---|:---|
| **Lago** | AGPLv3, self-host | Wallets + prepaid credits + metering are first-class; REST + webhooks; PSP connectors built in | ✅ **Chosen** |
| **Kill Bill** | Apache 2.0, self-host | Most complete subscription/invoicing engine; Java-native | Rejected — heavier operationally, usage-based metering is the weaker half, and its own domain model would fight ours |
| **OpenMeter** | Apache 2.0 → **acquired by Kong (2026)**, folding into Konnect | Excellent AI/token metering and entitlements | Rejected — vendor absorption is a strategic risk for a sovereignty-positioned product; the open-source trajectory is now uncertain |
| **Stripe Billing** (Meters GA, billing credits with immutable ledger; Metronome now a Stripe product) | Proprietary SaaS | Fastest to ship, genuinely excellent | Rejected as the *engine* — contradicts the Loi 25/GDPR sovereignty positioning that is Sovkora's differentiator. Retained as **PSP only**. |

**Licence note (must be read before phase 4).** AGPLv3 obligations attach to *modified* versions made
available over a network. Running an **unmodified** Lago as a separate process that Sovkora calls over
its REST API does not make Sovkora a derivative work. If you ever patch Lago's source, those patches
must be published. Practical rule: **never fork Lago — extend on the Sovkora side.** Lago also sells a
commercial licence if that constraint becomes uncomfortable; budget for it rather than fork.

### Integration seam

`external_plan_code` / `external_subscription_id` / `usage_event.external_synced_at` are the only
coupling points, and all three are nullable. **Phases 0–3 run with them NULL** — a complete,
functioning credit system with zero external dependency. Lago is then plugged in behind
`BillingEngineProvider` (an outbound port with a no-op local implementation first), which also keeps
the option of swapping Lago later without touching the ledger.

---

## 12. Admin plane — everything commercial is in the DB, editable live

**The governing rule: nothing commercial is in code or in yaml.** Offers, prices, entitlements,
packs and the behavioural switches are all rows, all editable from the operator console (`orazaka-web-client`), all
versioned. Deploying is never the way to change what Sovkora sells. This is the direct application
of AGENTS.md §4 (*domain data & behavior live in the DB*) and ADR-027/031 to the monetisation plane.

| Configuration | Table | Admin-editable | In yaml? |
|:---|:---|:---:|:---:|
| Plans, prices, monthly grants | `billing_plan` | ✅ | ❌ |
| Entitlement matrix (what each plan unlocks) | `billing_plan_entitlement` | ✅ | ❌ |
| Packages métier + their entitlements | `billing_pack(_entitlement)` | ✅ | ❌ |
| Credit rates per capability × model | `credit_pricebook` (versioned) | ✅ | ❌ |
| Enforcement mode, fail modes, hold TTL, thresholds | `billing_runtime_config` | ✅ (§13.5) | ❌ |
| Subscriptions, wallets, ledger adjustments | `billing_subscription`, `credit_wallet`, `credit_ledger_entry` | ✅ | ❌ |
| Service ports, timeouts, Lago URL & key, master wiring switch | — | ❌ | ✅ (§13.4/13.8) |

Backend: the resource controllers of §10 under `@PreAuthorize("hasRole('ADMIN')")`, every mutation
snapshotted into `billing_version_history` (attributable, diffable, rollback-able).

Frontend: the operator console of `orazaka-web-client` (admin host), through its BFF routes (the browser never calls :8095
directly — AGENTS.md §8), components from `orazaka-design-system`, ≤ 250 lines per `.tsx`.

| Screen | What the admin does | Non-negotiable |
|:---|:---|:---|
| **Plans** | CRUD plans, grants, prices, entitlement matrix, publish/unpublish, reorder tiers | Creating a 4th plan must require **zero deploy** — this is the robustness test of the whole design |
| **Packages** | CRUD packs métier, their entitlements, included credits, attach to plans | Same grammar as plans (§14) — one editor component serves both |
| **Pricebook** | Edit rates per capability × model, **preview margin impact from real `usage_event` history before publishing**, version + rollback | Never let a price be published without showing what it would have cost the last 30 days of traffic |
| **Wallets & ledger** | Inspect balance and full ledger, issue a manual `ADJUSTMENT` (credit **or** debit) with a mandatory reason | Every adjustment carries `created_by` + `reason`. Support goodwill must be auditable. |
| **Subscriptions** | View, change plan, cancel, grant a trial, force a period rollover | Changes emit `evt.subscription.*` — never a direct DB write |
| **Settings** (§13.5) | Enforcement mode, fail modes, hold TTL, overshoot, low-balance threshold | Typed + whitelisted; `ENFORCING` is a guarded action (§12.2) |
| **Usage analytics** | Credits by capability/model/plan, margin per capability, top consumers, refusal rate (402s) | The 402 rate is the pricing-health metric: high = pricing is wrong or the paywall is misplaced |

### 12.1 Adjustments — the one write that bypasses the protocol

Support must be able to hand back credits (a bad generation, an incident, a goodwill gesture) and to
claw them back (abuse, a duplicate top-up). That is an `ADJUSTMENT` ledger entry, and it is the only
path that writes credits without a hold. Its guardrails are therefore stricter, not looser:

* **Reason is mandatory**, free-text, stored on the entry — an unexplained balance change is
  indistinguishable from fraud six months later.
* **`created_by` is the admin's actor id**, never `'system'`.
* **Signed amount**, so a claw-back is the same operation as a grant — one code path, one audit trail.
* **Bucket must be chosen explicitly** (`GRANTED` or `PURCHASED`): refunding into `PURCHASED` gives
  credits that never expire. Defaulting this silently is how you accidentally hand out permanent
  balance.
* **A per-admin daily ceiling** (`billing.adjustment.daily-max-credits`) — a compromised admin
  account should not be able to mint unbounded balance. Above the ceiling, require a second admin.
* Emits `evt.credit.granted`, so the user's UI updates and the reconciliation job sees it.

### 12.2 Guarding the dangerous settings

Not every DB-backed setting deserves an unguarded toggle. `billing.enforcement.mode` in particular
changes whether the product takes money from people.

* **Whitelist**: the settings endpoint accepts only known keys with a declared type and enum domain.
  An admin cannot type `ENFORCNG` and silently brick authorisation — an unknown value is a 400, and
  the reader falls back to the code default rather than to chaos.
* **Confirmation + snapshot**: any transition into `ENFORCING`, and any pricebook publication, writes
  a `billing_version_history` row and requires an explicit confirm step in the UI.
* **Rollback is one click** — restore the previous snapshot. This is the mechanism that makes the
  §13.2 "2am incident" argument real rather than aspirational.

The client side gets the mirror surface in `orazaka-web-client`: balance in the header, cost estimate
*before* confirming an expensive job, low-balance banner driven by `evt.wallet.low-balance`, ledger
history, top-up flow. **Showing the price before the click is not a UI nicety** — an unexpected debit
on a job the user did not know was expensive is the number-one support ticket in credit products.

---

## 13. Configuration, kill-switches & environments

The requirement — *run Sovkora with billing blocking or not, so it can be disabled in dev and test* —
is not one switch. Conflating it into a single `billing.enabled=true` in `application.yaml` is the
obvious answer and the wrong one, for a reason the codebase already documents: **ADR-031's three
tiers** and the bootstrap boundary.

### 13.1 Four switches, three homes

| # | Switch | Question it answers | Home | Existing precedent |
|:--|:---|:---|:---|:---|
| 1 | `orazaka.billing.enabled` | Is the billing subsystem **wired at all**? (bean-level) | **`application.yml` + `.env`**, typed `@ConfigurationProperties` | `orazaka.core.orchestration.pipeline.enabled` — the §7 full-bypass kill-switch |
| 2 | `billing.enforcement.mode` | Does a refusal **block**, or only log? | **DB** (`billing_runtime_config`) | `rate-limit.enabled` — seeded `false`, DB-driven (ADR-031) |
| 3 | `billing.fail-mode.{chat,media}` | Billing unreachable → allow or refuse? | **DB** | — |
| 4 | `orazaka.billing.engine.*` | Where is Lago, is it plugged in? | **`application.yml` + `.env`** (billing-service only) + compose profile | `spring.rabbitmq.*` wiring pattern |

### 13.2 Why enforcement must **not** live in `application.yaml`

ADR-031's rule is precise: yaml holds what is needed **before** the DB exists; the DB holds
**post-startup behaviour an admin changes live**. Enforcement mode is squarely the second — and
there is an exact precedent already in the seed data:

```sql
('rate-limit.enabled', 'false', 'boolean', 'Master toggle for per-user rate limiting …')
```

Rate limiting is the same shape of concern (a per-user gate you want off in dev, on in prod,
flippable without a redeploy) and it deliberately lives in `orazaka_runtime_config`, not in yaml.
Billing enforcement should not diverge from it.

The operational argument is the decisive one: **if the mode is in yaml, "stop blocking users" is a
deploy.** Pricing goes wrong at some point — a mis-calibrated video rate drains everyone's wallet on
a Saturday. You want that to be one admin click, not a rebuild and a rolling restart. Putting the
lever in the DB is what makes the incident survivable.

### 13.3 Three modes, not a boolean

```
OFF        no hold, no ledger write, no usage_event         — a contributor can run the stack
                                                              without the billing service at all
DRY_RUN    hold computed · usage_event written · debit
           recorded · refusals LOGGED but NEVER enforced    — phase 0/1 shadow metering (§16)
ENFORCING  refusals return 402/403                          — production
```

A boolean would lose `DRY_RUN`, and `DRY_RUN` is the whole point of phase 0: it is what produces the
**measured** pricebook (§7) instead of a guessed one, and it is how enforcement reaches production
without a step change. Ship `DRY_RUN` first, read the would-have-refused rate for a week, then flip.

### 13.4 The wiring switch, and the Null Object that keeps it clean

```yaml
# services/{conversation,job,automation}-service/…/application.yml
# .env is the single source of truth — this file only reads ${ENV:default}.
orazaka:
  billing:
    enabled: ${BILLING_ENABLED:false}          # ← default OFF: a fresh clone runs without billing
    client:
      base-url:        ${BILLING_INTERNAL_URL:http://localhost:8095}
      connect-timeout: ${BILLING_CONNECT_TIMEOUT:500ms}
      read-timeout:    ${BILLING_READ_TIMEOUT:2s}
```

Timeouts stay in yaml on purpose — ADR-031 classifies them as bootstrap/infra, alongside
`jobs.execution-timeout`.

**Do not scatter `if (billingEnabled)` across call sites.** That is ERR-127 territory and it puts a
monetisation concern into every producer. The port has two adapters, selected at bootstrap:

```java
@Bean
@ConditionalOnProperty(prefix = "orazaka.billing", name = "enabled", havingValue = "true")
CreditAuthorizationClient httpCreditAuthorizationClient(…) { … }

@Bean
@ConditionalOnMissingBean(CreditAuthorizationClient.class)
CreditAuthorizationClient noOpCreditAuthorizationClient() {
  return new NoOpCreditAuthorizationClient();   // always grants, records nothing
}
```

Call sites never learn whether billing exists. This is the same discipline as the provider mesh: one
port, adapters swapped by config.

### 13.5 The mode lives in **one** service — the protocol carries the outcome, not the policy

`billing.enforcement.mode` is read **only by the billing service**, from its own database. Consumers
never read it. They call `hold()` and receive:

| Mode | Response to the consumer | Consumer behaviour |
|:---|:---|:---|
| `OFF` | (NoOp adapter — no call is made) | proceed |
| `DRY_RUN` | `201 {status: GRANTED, dryRun: true, wouldHaveRefused: true}` | proceed, log |
| `ENFORCING`, sufficient | `201 {status: GRANTED}` | proceed |
| `ENFORCING`, insufficient | `402 {status: INSUFFICIENT, balance, required, remedies}` | block, render paywall |

This is what keeps the switch honest: **one owner, one row, no duplicated policy across four
services**. The alternative — every producer reading the mode — guarantees the day where the
conversation-service enforces and the job-service does not.

Because the billing service owns its own database (§4), it carries its **own copy** of the runtime
config table rather than reading the config plane's `orazaka_runtime_config` — contract-copy
doctrine, no cross-database read:

```sql
-- infra/initdb/70-billing.sql — same shape as orazaka_runtime_config, this service's own copy.
CREATE TABLE billing_runtime_config (
    config_key   VARCHAR(120) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    value_type   VARCHAR(20)  NOT NULL DEFAULT 'string',
    description  TEXT
);

INSERT INTO billing_runtime_config (config_key, config_value, value_type, description) VALUES
('billing.enforcement.mode',        'DRY_RUN',     'string', 'OFF | DRY_RUN | ENFORCING — master gate. DRY_RUN meters without blocking.'),
('billing.fail-mode.chat',          'FAIL_OPEN',   'string', 'Billing unreachable on the sync path: FAIL_OPEN protects UX, records a reconciliation debt.'),
('billing.fail-mode.media',         'FAIL_CLOSED', 'string', 'Billing unreachable on a high-cost job: FAIL_CLOSED protects margin.'),
('billing.hold.ttl-seconds',        '1800',        'int',    'Sweeper releases ACTIVE holds older than this (§6.3).'),
('billing.credit.unit-price-cents', '1',           'int',    'Money anchor: cents per credit at list price (§8).'),
('billing.overshoot.max-credits',   '50',          'int',    'Bounded settlement overshoot allowed rather than killing a stream mid-token (§17).'),
('billing.low-balance.percent',     '10',          'int',    'Threshold that emits evt.wallet.low-balance.'),
('billing.adjustment.daily-max-credits', '10000',  'int',    'Per-admin ceiling on manual ADJUSTMENT entries (§12.1).')
ON CONFLICT (config_key) DO NOTHING;
```

Read through a `RuntimeConfigProvider`-shaped port with a code default on every getter, so deleting
a key reverts cleanly instead of failing at startup.

**These rows are admin-editable**, not just seeded: `ConfigurationController`
(`PATCH /api/v1/billing/configuration`, `hasRole('ADMIN')`) backs the Settings screen of §12, with
the whitelist + confirmation + snapshot guardrails of §12.2. The seed is the *initial* value, never
the authority — an admin change must not be reverted by the next `orazaka start`, which is why these
keys live in a table with `ON CONFLICT DO NOTHING` rather than in a file that is reapplied.

### 13.6 Defaults per environment

| Environment | `BILLING_ENABLED` | `billing.enforcement.mode` | Rationale |
|:---|:---:|:---|:---|
| Fresh clone / `orazaka dev` | `false` | — | A contributor must be able to run the stack without the billing service or Lago. **This is the default in `.env.example`.** |
| Unit + integration tests | `false` | — | `@DynamicPropertySource` pins it off; no test spins a billing container it does not need |
| Billing integration tests | `true` | `ENFORCING` | Testcontainers billing DB; the enforcement path is what these tests exist to cover |
| Hermetic E2E (`orazaka test e2e`) | `true` | `DRY_RUN` by default; **one dedicated scenario flips `ENFORCING`** | The whole suite must not be re-costed, but the paywall path is the quality gate for phase 2 |
| Production (post-phase-4) | `true` | `DRY_RUN` → `ENFORCING` after calibration | §16 phase 2 |

The asymmetry is deliberate: **default off everywhere, explicitly on where it is the subject under
test**. A billing system that must be running for the chat E2E to pass is a billing system that
blocks every contributor.

### 13.7 Lago: a compose profile, not a new default dependency

Lago is stateful infra in Docker — allowed by AGENTS.md §0/§1. But it must not join the default
`orazaka start` path: it is four extra containers, and the local loop is already at the edge of a
64 GB machine.

**Use a Compose profile.** `orazaka start` invokes `docker compose … up -d db-vector redis rabbitmq`
with explicit service names, so a profiled service is simply never selected — **no CLI change is
required**, which is the cheapest correct answer.

**The corollary, which is easy to get backwards**: because the CLI names its services explicitly,
`COMPOSE_PROFILES=billing orazaka start` also selects nothing. The env var only makes a profiled
service *eligible*; it does not add it to an explicit service list. Lago is therefore brought up
with a direct `docker compose` invocation (below), not through the CLI.

**Interpolation does not come from the root `.env`.** The compose file lives in `infra/`, so
Compose's project directory is `infra/` and the repo-root `.env` — the documented single source of
truth — is *not* read automatically. Two consequences: pass `--env-file .env` from the repo root
when starting Lago, and give every `${LAGO_*}` reference an explicit default so the **default**
(non-billing) path does not emit `variable is not set` warnings on every `orazaka start`.

```yaml
# infra/docker-compose.yml — appended. Inert unless COMPOSE_PROFILES=billing.
  # ⚠ Lago v1.45.1's `db:migrate` loads a structure.sql requiring the pg_partman extension, which
  # stock postgres:16-alpine does NOT ship: the API boots but has no schema. Plugging Lago in for
  # real (phase 4) needs a partman-capable pinned image here.
  lago-db:
    image: postgres:16-alpine
    container_name: orazaka-lago-db
    profiles: [billing]
    environment:
      POSTGRES_DB: lago
      POSTGRES_USER: lago
      POSTGRES_PASSWORD: ${LAGO_DB_PASSWORD:-lago}
    volumes:
      - lago_data:/var/lib/postgresql/data     # SEPARATE volume — see note below
    networks: [orazaka-isolated]

  lago-redis:
    image: redis:7-alpine
    container_name: orazaka-lago-redis
    profiles: [billing]
    networks: [orazaka-isolated]

  lago-api:
    image: getlago/api:v1.45.1                 # PINNED — AGENTS.md §11 bans floating versions
    container_name: orazaka-lago-api
    profiles: [billing]
    depends_on: [lago-db, lago-redis]
    environment: &lago-api-env
      # NOT optional: without it the image boots Rails in `development`, whose gem group
      # (annotate_rb…) is absent from the published image — api and worker crash-loop on LoadError.
      RAILS_ENV:        production
      DATABASE_URL:     postgresql://lago:${LAGO_DB_PASSWORD:-lago}@lago-db:5432/lago
      REDIS_URL:        redis://lago-redis:6379
      SECRET_KEY_BASE:  ${LAGO_SECRET_KEY_BASE:-}
      LAGO_RSA_PRIVATE_KEY: ${LAGO_RSA_PRIVATE_KEY:-}
      LAGO_API_URL:     http://localhost:${LAGO_API_PORT:-8098}
      LAGO_FRONT_URL:   http://localhost:${LAGO_FRONT_PORT:-8097}
    ports:
      - "127.0.0.1:${LAGO_API_PORT:-8098}:3000"    # ⚠ Lago listens on 3000 — collides with
                                                   #   orazaka-web-client. Remap on the host.
                                                   #   8098, not 8096: ADR-034 gave 8096 to the
                                                   #   Studio service (STUDIO_PORT).
    networks: [orazaka-isolated]

  lago-worker:
    image: getlago/api:v1.45.1
    container_name: orazaka-lago-worker
    profiles: [billing]
    command: ["./scripts/start.worker.sh"]
    depends_on: [lago-db, lago-redis]
    environment: *lago-api-env
    networks: [orazaka-isolated]

  lago-front:
    image: getlago/front:v1.45.1
    container_name: orazaka-lago-front
    profiles: [billing]
    ports:
      - "127.0.0.1:${LAGO_FRONT_PORT:-8097}:80"    # ⚠ 80 internally; 8080 would collide with
                                                   #   the conversation-service
    networks: [orazaka-isolated]

volumes:
  pgvector_data:
  lago_data:      # deliberately NOT the pgvector volume
```

Three decisions in that block worth stating explicitly:

1. **Lago gets its own Postgres container and its own volume**, not a database inside
   `orazaka-db-vector`. Two reasons. Lago manages its schema with Rails migrations, which does not
   belong in `infra/initdb/` (that directory is "one file per Sovkora bounded context", scanned by
   `SqlBoundaryRules`). And more importantly: **`orazaka stop --purge` must never be able to delete
   financial records along with disposable dev data.** Separate lifecycle, separate volume.
   *Verified mechanism*: `stop --purge` runs `docker compose … down --timeout 5 -v` **without** the
   billing profile, so it never selects the Lago services and `lago_data` survives. The separate
   volume is necessary but not sufficient — it is the profile that does the protecting, and a future
   CLI change that adds `COMPOSE_PROFILES` to `stop` would silently break this guarantee. The side
   effect today is that `stop --purge` leaves Lago's containers behind in the `Exited` state;
   removing them is `COMPOSE_PROFILES=billing docker compose -p orazaka -f infra/docker-compose.yml down`.
2. **Ports are remapped.** Lago's API defaults to `3000` (collides with `orazaka-web-client`) and its
   front to `80`/`8080` (collides with the conversation-service). Loopback-bound, consistent with the
   rest of the compose file.
3. **Image tags are pinned** to the version this design was validated against — AGENTS.md §11.

Usage:

```bash
orazaka start                               # infra only — unchanged default

# infra + Lago. NOT `COMPOSE_PROFILES=billing orazaka start`: the CLI passes explicit service
# names, so the profile never gets a chance to select anything. Run from the repo root.
COMPOSE_PROFILES=billing docker compose -p orazaka --env-file .env \
  -f infra/docker-compose.yml up -d
```

Making it first-class in the CLI is therefore a real code change, not an env var:
`orazaka start --with-billing` would have to append the five service names (or drop the explicit
list) in `start.command.ts`. Deferred — the direct `docker compose` invocation is the phase-4
workflow and phase 4 is gated.

### 13.8 Lago wiring belongs to the billing service **only**

```yaml
# krizaka/krizaka-billing/krizaka-billing-service/…/application.yml
orazaka:
  billing:
    engine:
      enabled:  ${LAGO_ENABLED:false}          # ← the seam stays dormant through phases 0–3
      base-url: ${LAGO_API_URL:http://localhost:8098}
      api-key:  ${LAGO_API_KEY:}
      sync-batch-size:  ${LAGO_SYNC_BATCH_SIZE:200}
      connect-timeout:  ${LAGO_CONNECT_TIMEOUT:2s}
      read-timeout:     ${LAGO_READ_TIMEOUT:10s}
```

Same Null Object discipline: `BillingEngineProvider` has a `LagoBillingAdapter` when
`enabled=true` and a `NoOpBillingEngineProvider` otherwise. **Phases 0–3 run with `LAGO_ENABLED=false`
and are a complete, enforcing credit system** — the outbox simply has no consumer draining
`evt.usage.recorded` to an external engine yet.

No other service ever sees a Lago property. If `LAGO_*` appears in the conversation-service's yaml,
the seam has leaked.

`.env` additions (and `.env.example` with `CHANGE_ME` — **`LAGO_API_KEY` is a secret and is
generated in the Lago UI**, so it is never committed):

Both files already number their sections, so these append as **15** and **16** — the numbers below
are positional, not fixed. Note also that `.env` is read by `properties-maven-plugin` as a **Java
properties file**: a trailing `# comment` after a value becomes part of the value, so every comment
goes on its own line, and every value stays on one line.

```bash
# ── 15. BILLING & CREDITS ─────────────────────────────────────────────────────
# Master wiring switch only — the enforcement mode lives in billing_runtime_config.
BILLING_ENABLED=false
BILLING_PORT=8095
BILLING_INTERNAL_URL=http://localhost:8095
# Own database + role, created by infra/initdb/70-billing.sql (billing-service only).
BILLING_DB_URL=jdbc:postgresql://localhost:5432/krizaka_billing_db
BILLING_DB_USERNAME=krizaka_billing
BILLING_DB_PASSWORD=krizaka_billing_pass
# ── 16. LAGO (opt-in: see the compose invocation in §13.7) ────────────────────
LAGO_ENABLED=false
LAGO_API_PORT=8098
LAGO_FRONT_PORT=8097
LAGO_API_URL=http://localhost:8098
# Secret, created in the Lago UI after first boot — no value exists until then.
LAGO_API_KEY=CHANGE_ME
LAGO_DB_PASSWORD=CHANGE_ME
LAGO_SECRET_KEY_BASE=CHANGE_ME
# Base64 RSA key on ONE line — `| tr -d '\n'` is required, base64 wraps by default:
#   openssl genrsa 2048 | base64 | tr -d '\n'
LAGO_RSA_PRIVATE_KEY=CHANGE_ME
```

The `BILLING_DB_*` triplet mirrors the `IDENTITY_DB_*` / `AUTOMATION_DB_*` / `KNOWLEDGE_DB_*` blocks
every other own-database context carries; it is consumed only by the billing service, from an
`orazaka.billing.datasource` prefix (§19 tranche 2).

### 13.9 One rule to keep this from rotting

**A billing switch is either read before the DB exists (yaml) or flipped live by an admin (DB).
Nothing is in both.** Duplicating `enabled` in yaml *and* the DB is how you end up debugging why
production is refusing requests that the config says it should allow — the failure mode AGENTS.md §4
exists to prevent ("each value declared once").

---

## 14. Packages métier — modelled now, sold later

The ultimate goal (workflow bundles sold by profession) needs **no new machinery** — it is the
entitlement grammar applied to a different sellable object:

```
package.marketing-studio = true
   ⇒ unlocks workflow templates tagged 'marketing-studio' in the automation service
   ⇒ optionally includes N credits at purchase
   ⇒ optionally raises concurrency.jobs
```

A user's effective entitlements = **plan entitlements ∪ package entitlements**, resolved in
`EntitlementService` with a documented conflict rule (**most permissive wins** for booleans, **max**
for numeric limits). Write that rule down once; ambiguity here produces support tickets that are
impossible to answer.

Two things to get right now, cheaply, so the later phase is additive:

1. **Tag workflow templates with a `pack_key` from day one** in the automation service, even
   while every workflow is free. Retrofitting ownership onto existing templates is painful.
2. **Meter the orchestration `CALL` fee from day one** (§6.5), even at 0 credits. It gives you the
   usage history to price the marketplace when you launch it, instead of guessing.

That is the whole delta. Phase 5 becomes an admin screen and a catalogue page, not an architecture.

---

## 15. Fitness functions — what the build enforces

Consistent with the project's culture of build-enforced rules rather than documented intentions.
Add to `BillingGovernanceTest` (reusing `GovernanceRules` from `orazaka-test-support`):

| Rule | Enforcement | Rationale |
|:---|:---|:---|
| **No plan key literal in Java** | ArchUnit: `"free"`/`"premium"`/`"ultimate"` banned outside the persistence adapter & test fixtures | A hardcoded plan name is a deploy required to add a plan — the exact failure the brief asks to avoid |
| **No commercial config in yaml** | Test: no `application.yml` under `apps/ and services/` matches `plan\|credit\|pricebook\|entitlement\|enforcement` outside the `orazaka.billing.{enabled,client,engine}` wiring keys | AGENTS.md §4 — offers and behaviour are DB data; only wiring is yaml (§12) |
| **Enforcement mode declared once** | Test: `billing.enforcement.mode` appears in `billing_runtime_config` and in **no** yaml/env | The duplicated-switch failure mode of §13.9 |
| **Settings whitelist is total** | Test: every key in `billing_runtime_config` has a declared type + domain in the whitelist, and an unknown key/value is rejected with 400 | An admin must not be able to brick authorisation with a typo (§12.2) |
| **No price literal in Java** | ArchUnit: numeric credit constants banned outside `PricingService`'s pricebook read | Pricing is data (ADR-027) |
| **Every catalogued model is priced** | Admin-console check (not a DB constraint — `orazaka_models` and `credit_pricebook` are different databases, SEAM-001): every `is_active` model resolves to a current pricebook row before `ENFORCING` | §8.1 makes an unpriced model a refusal. That is correct but useless as a surprise — the gap must be visible in the console, and in the `DRY_RUN` log, *before* the flip |
| **Ledger is append-only** | DB: a `BEFORE UPDATE OR DELETE` trigger that raises (§5.2) — **not** a `REVOKE`, which the table owner can bypass; plus ArchUnit: `CreditLedgerEntryRepository` exposes no `save`-on-existing / `delete` | The financial record must be immutable *by construction*, not by discipline |
| **No cross-context FK** | existing `SqlBoundaryRules` [SEAM-001] on `70-billing.sql` | already enforced |
| **Every settle is idempotent** | integration test: replay the same `messageId` twice, assert one ledger entry | double-debit is unrecoverable trust damage |
| **Holds cannot leak** | integration test: expire a hold, assert the sweeper releases it and `held` returns to 0 | the most commonly forgotten component |
| **Balance never negative** | property test with concurrent holds against the `CHECK` + conditional `UPDATE` | the core financial invariant |

---

## 16. Roadmap

Each phase ends on a green gate (unit + IT + hermetic E2E through the edge) and is independently
revertible — the same discipline as ADR-032's phases.

| Phase | Delivers | Depends on | Gate |
|:---|:---|:---|:---|
| **0 — Shadow metering** | `70-billing.sql` (incl. `billing_runtime_config`), billing-service skeleton, `orazaka.billing.enabled` + `NoOp` adapter, workers report `gpuSeconds`/`frames`/`steps`, `usage_event` written, mode `OFF` | — | The full stack still starts with `BILLING_ENABLED=false`; 2 weeks of real usage data → *calibrated* pricebook (§7) |
| **1 — Ledger + hold/settle, `DRY_RUN`** | wallet, ledger, holds, pricebook, sweeper, `EntitlementInterceptor`; mode `DRY_RUN` — refusals logged, never enforced | 0 | E2E: a chat turn produces a correct hypothetical debit; balance never goes negative under concurrency |
| **2 — Enforcement** | flip mode to `ENFORCING`: **async media first** (highest cost, clearest UX), then sync chat; 402/403 surfaces in the UI; fail-modes tuned | 1 | E2E: a drained wallet blocks a video job; a failed job refunds fully; the mode flip is a DB write, not a deploy |
| **3 — Plans, entitlements, admin plane** | plan/package/entitlement tables, subscription lifecycle, `evt.subscription.*`, **all seven admin screens incl. Settings + adjustments**, `billing_version_history` + rollback, client balance UI, top-up **without payment** (admin-granted) | 2 | Creating a 4th plan **and** flipping enforcement, both end-to-end from the admin console with **zero code change** |
| **4 — Money** ⚠️ **gated on leaving the local phase (AGENTS.md §0)** | Lago in `docker-compose`, `BillingEngineProvider` adapter, PSP + webhooks, real top-up & self-serve upgrade, invoices, dunning | 3 + explicit go-live decision | Reconciliation job shows zero drift between ledger and Lago over a full period |
| **5 — Packages métier & workflow marketplace** | pack catalogue, workflow templates tagged by package, per-profession bundles, marketplace revenue share | 3 (4 for paid packs) | A package purchase unlocks its workflows with no engine change |

**Phases 0–3 are entirely local and violate nothing.** Phase 4 is the only one that introduces a
remote dependency, and it is explicitly flagged rather than smuggled in — the contract says do not
implement cloud/remote-secret concerns until asked. Build the seam now, plug the money in when the
product leaves the laptop.

---

## 17. Risks & open questions

| # | Risk | Mitigation |
|:---|:---|:---|
| 1 | **Ledger ↔ Lago drift** (two systems, one truth) | Nightly reconciliation replaying `credit_ledger_entry.balance_after` and diffing against Lago's wallet; alert on any delta. Sovkora's ledger wins for authorisation; Lago wins for invoices. Never auto-heal — a drift is an incident to investigate. |
| 2 | **A credit's meaning drifts** as models change | `1 credit = 1k tokens on the default local model` is frozen in ADR-033; model cost changes move the *pricebook*, never the credit definition. Re-pricing is versioned and announced. |
| 3 | **Estimate ≫ reality** on chat (a hold too large blocks a user who has enough credit) | Small conservative estimate + settle upward; if settlement exceeds the hold, allow a bounded overshoot (`billing.overshoot.max-credits`) and record it, rather than killing a stream mid-token |
| 4 | **Free-tier abuse** (multi-account credit farming) | No top-up on `free` (§8); grant tied to a verified email; correlate by device/IP at signup; keep `free` grants small enough that farming is more expensive than paying |
| 5 | **Billing service is a new SPOF on the hot path** | `billing.fail-mode` per capability (§6.2) + entitlement cache means chat degrades gracefully; only high-cost media fails closed |
| 6 | **AGPLv3 obligations** | Never fork Lago; extend on the Sovkora side (§11) |
| 7 | **Refund policy for "bad" outputs** | A technically successful but subjectively poor generation is **billed**. Goodwill goes through an auditable `ADJUSTMENT` in admin, never through silent hold release. Decide and publish this before launch. |

**Open questions to settle before phase 3** — each is a product decision, not an architecture one:

1. Do purchased credits ever expire? (recommend: 12 months, disclosed at purchase)
2. Does an annual plan grant 12× monthly upfront, or monthly? (recommend: monthly grant, annual price — protects cash flow and reduces expiry disputes)
3. On downgrade, is the excess granted balance kept or truncated? (recommend: kept until period end, then normal expiry)
4. Team/organisation wallets — shared pool or per-seat? (deferred to BIZ-03, but the `actor_id` column should be understood as *billable subject*, which may later be an org id — the schema already permits it)

---

## 18. Relationship to existing documents

* [`AGENTS.md`](../AGENTS.md) §0 (local phase), §4 (config vs data), §5 (own DB, no cross-context FK), §6 (broker doctrine), §7 (interceptor short-circuit) — all upheld; §0 is the reason phase 4 is gated.
* `ADR-027` / `ADR-031` — the pricebook and plan catalogue are *domain data in the DB*, exactly the pattern those ADRs mandate.
* `ADR-032` — the billing service follows the same extraction doctrine: own database, own role, contract-copy, edge route.
* [`MASTER_FEATURES.md`](MASTER_FEATURES.md) — this document is the design for **BIZ-02** (integrated billing) and the prerequisite for **BIZ-03** (organisations).
* `ADR-033` — the decision record for the choices argued here.
* [`.agent/workflows/implement_billing.md`](../.agent/workflows/implement_billing.md) — the **executable** workflow derived from this design (file manifest, verbatim SQL, exact contract signatures, acceptance gates). This document says *why*; that one says *do this*.

---

## 19. Implementation manifest

Three tranches, each an independently revertible green commit. **Tranche 1 is the only one with no
Spring wiring risk** — it is data and contracts, and everything else depends on it. Full step-by-step
instructions live in [`.agent/workflows/implement_billing.md`](../.agent/workflows/implement_billing.md).

### Tranche 1 — Foundations (schema + Tier-1 contract + infra)

| Action | Path |
|:---|:---|
| create | `infra/initdb/70-billing.sql` — §5 + §13.5 schema, immutability trigger, dev seed |
| modify | `infra/initdb/00-reset.sql` — drop `krizaka_billing_db` / role `krizaka_billing` |
| create | `krizaka/krizaka-billing/krizaka-billing-api/pom.xml` — pure JDK, JUnit test scope only |
| create | `…/krizaka-billing-api/src/main/java/com/krizaka/billing/domain/model/` — `BillableCapability`, `BillableUnit`, `CreditHoldCommand`, `CreditHoldResponse`, `SettleCreditCommand`, `EntitlementSnapshot`, `HoldStatus`, `EnforcementMode` |
| create | `…/krizaka-billing-api/src/main/java/com/krizaka/billing/domain/port/` — `CreditAuthorizationClient`, `EntitlementProvider` |
| create | `…/krizaka-billing-api/src/main/java/com/krizaka/billing/domain/exception/InsufficientCreditsException.java` |
| create | one mirroring `*Test.java` per record (ERR-103) |
| modify | `pom.xml` — register the module next to `krizaka-users-api` |
| modify | `.env`, `.env.example` — §13.8 blocks 10 & 11 |
| modify | `infra/docker-compose.yml` — §13.7 Lago services under `profiles: [billing]` + `lago_data` volume |

Gate: `./mvnw -q -pl krizaka/krizaka-billing/krizaka-billing-api test` green; `orazaka stop --purge && orazaka start` recreates every database including `krizaka_billing_db`; `SqlBoundaryRules` green; default `orazaka start` still starts exactly four containers.

### Tranche 2 — `krizaka-billing-service` :8095

Entities/repositories for the §5 tables, `CreditLedgerService` (hold/settle/release, single-statement
conditional `UPDATE`), `PricingService` (pricebook resolution + version pinning), `EntitlementService`
(plan ∪ pack), `BillingConfigurationService` (whitelisted `billing_runtime_config`), the seven
resource controllers of §12, `JobSettlementListener`, the hold sweeper, `BillingServiceGovernanceTest`.
Own `DataSource` from an `orazaka.billing.datasource` prefix (ADR-032's env-precedence lesson).

Gate: integration tests for the four invariants of §15 (idempotent settle, no leaked hold, balance
never negative, ledger immutable).

### Tranche 3 — Consumer wiring, mode `DRY_RUN`

`NoOpCreditAuthorizationClient` + `HttpCreditAuthorizationClient` (`@HttpExchange`) selected by
`@ConditionalOnProperty`, `EntitlementInterceptor` in `orazaka-interceptors/governance/` (policy row
at `execution_order = 4`, the free slot), hold on job submission in `job-service`, settle from engine
metadata, `TokenUsage` record replacing the untyped `InternalChatResponse.metadata` entries.

Gate: hermetic E2E — a chat turn and a media job both produce a correct hypothetical debit with
`BILLING_ENABLED=true` and mode `DRY_RUN`, and the stack still boots green with `BILLING_ENABLED=false`.
