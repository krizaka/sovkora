---
description: BILLING & CREDITS — PHASED IMPLEMENTATION (tranche 1 = foundations)
---

# Workflow: Implement the Billing & Entitlements context

Design intent: [`docs/BILLING_ARCHITECTURE.md`](../../docs/BILLING_ARCHITECTURE.md).
Decision record: [krizaka-internal docs/adr/sovkora/ADR-033-credit-metering-and-billing.md](https://github.com/krizaka/krizaka-internal/blob/main/docs/adr/sovkora/ADR-033-credit-metering-and-billing.md).
**That design document is normative for *what* and *why*. This workflow is normative for *how* and
*in what order*. Where they disagree, the design document wins and this file is corrected.**

## §0 Init

1. Load `AGENTS.md` (the governance contract) — §0, §4, §5, §6, §11 all bind this work.
2. Load `.agent/rules/naming_conventions.md`, `configuration_standards.md`, `messaging_standards.md`,
   `testing_standards.md`.
3. Read `docs/BILLING_ARCHITECTURE.md` §5 (data model), §6 (protocol), §10 (ports), §12 (admin),
   §13 (configuration), §15 (fitness functions), §19 (manifest).
4. Read for pattern-matching **before writing anything**:
   - `infra/initdb/10-identity.sql` and `50-automation.sql` — the own-database initdb shape
   - `krizaka/krizaka-build/krizaka-users/krizaka-users-api/` — the Tier-1 contract module shape
   - `services/knowledge/sovkora-knowledge-service/` — the smallest complete service shape
   **Mirror these. Do not invent a new structure.**

## §1 Scope of THIS run — tranche 1 only

Tranche 1 is **schema + Tier-1 contract + infra config**. It contains **no Spring bean, no service
module, no controller, no consumer wiring**. It is deliberately the slice with zero wiring risk,
because everything else depends on it.

**In scope** — exactly the file manifest of §3.
**Out of scope, do NOT start** — `krizaka-billing-service`, entities, repositories, controllers,
`EntitlementInterceptor`, the NoOp/HTTP client adapters, admin UI, Lago adapter. Those are tranches
2–3 (design §19). If tranche 1 is green and you have budget left, **stop and report**, do not
continue.

## §2 Non-negotiable constraints

| Rule | Applies here as |
|:---|:---|
| AGENTS.md §0 — 100% local | No CI, no cloud, no remote secret. Lago is added to compose but **profiled off**; `LAGO_API_KEY` is `CHANGE_ME` in `.env.example`. |
| AGENTS.md §5 — own DB, no cross-context FK | `70-billing.sql` creates its own role + database. **Every `REFERENCES` must target a table created in the same file.** `actor_id` is an opaque `VARCHAR(255)`, never an FK. `SqlBoundaryRules` fails the build otherwise. |
| AGENTS.md §5 — own dedup copy | The billing DB carries its **own** `processed_messages` (contract-copy), not a shared one. |
| AGENTS.md §4 — config vs data | Plans, prices, entitlements, switches are **rows**. Nothing commercial in yaml. |
| AGENTS.md §11 — pinned versions | Lago images pinned to `v1.45.1`. No `latest`, no `-SNAPSHOT`. |
| ERR-103 | One top-level type per `.java` file **+ one mirroring test file**. |
| ERR-106/116 | Records validate in the **compact constructor**. No service-side null guards. |
| ERR-104 | No `Orazaka` class prefix. |
| Tier-1 purity | `krizaka-billing-api` has **zero** implementation dependencies — no Spring, no Jackson, no JPA. Pure JDK + JUnit (test scope). It must **not** import `com.krizaka.orazaka.business.*` (Tier-3): `BillableCapability` is a deliberate contract-copy of `business.api.Capability`. |

## §3 File manifest — tranche 1

```
CREATE  infra/initdb/70-billing.sql
MODIFY  infra/initdb/00-reset.sql                       (+ billing drop block)
CREATE  krizaka/krizaka-billing/krizaka-billing-api/pom.xml
CREATE  krizaka/krizaka-billing/krizaka-billing-api/src/main/java/com/krizaka/billing/domain/model/BillableCapability.java
CREATE  …/domain/model/BillableUnit.java
CREATE  …/domain/model/EnforcementMode.java
CREATE  …/domain/model/HoldStatus.java
CREATE  …/domain/model/CreditHoldCommand.java
CREATE  …/domain/model/CreditHoldResponse.java
CREATE  …/domain/model/SettleCreditCommand.java
CREATE  …/domain/model/EntitlementSnapshot.java
CREATE  …/domain/port/CreditAuthorizationClient.java
CREATE  …/domain/port/EntitlementProvider.java
CREATE  …/domain/exception/InsufficientCreditsException.java
CREATE  …/src/test/java/com/krizaka/billing/domain/model/*Test.java   (one per record — ERR-103)
MODIFY  pom.xml                                          (+ <module> next to krizaka-users-api)
MODIFY  .env                                             (+ §6 blocks)
MODIFY  .env.example                                  (+ §6 blocks, secrets = CHANGE_ME)
MODIFY  infra/docker-compose.yml                         (+ Lago services, profiles: [billing])
```

## §4 `infra/initdb/70-billing.sql` — write it verbatim

Header comment must follow the house style of `10-identity.sql` (owner, purpose, opaque-ActorId
note). Then, in this order:

```sql
CREATE ROLE krizaka_billing LOGIN PASSWORD 'krizaka_billing_pass';
CREATE DATABASE krizaka_billing_db OWNER krizaka_billing;
\c krizaka_billing_db
SET ROLE krizaka_billing;
```

Then the tables **exactly as specified in `docs/BILLING_ARCHITECTURE.md`**:

| § of the design | Tables |
|:---|:---|
| §5.1 | `billing_plan`, `billing_plan_entitlement`, `billing_pack`, `billing_pack_entitlement`, `billing_subscription` (+ the partial unique index on active status) |
| §5.2 | `credit_wallet` (+ `ck_wallet_non_negative`), `credit_ledger_entry` (+ `idx_ledger_idempotency` UNIQUE, + the **immutability trigger**), `credit_hold` (+ sweeper & job indexes) |
| §5.3 | `credit_pricebook` (+ `idx_pricebook_current`), `usage_event` (+ both indexes) |
| §5.4 | `billing_version_history`, `billing_outbox`, `processed_messages` |
| §13.5 | `billing_runtime_config` |

**Do not omit the immutability trigger** (design §5.2) — it is a fitness function, not decoration:

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

### Seed data (dev only, idempotent)

All inserts `ON CONFLICT … DO NOTHING`, in this order:

1. **`billing_runtime_config`** — the **eight** keys of design §13.5, with
   `billing.enforcement.mode = 'DRY_RUN'`.
2. **`billing_plan`** — `free` / `premium` / `ultimate` with the grants, prices and
   `rate_limit_tier_key` of design §8. `external_plan_code` stays `NULL` (phase 4 is gated).
3. **`billing_plan_entitlement`** — the full matrix of design **§8.2** (11 keys × 3 plans), which is
   the normative key vocabulary, not an illustration.
4. **`credit_pricebook`** — version `1`, `effective_to = NULL`, taking `estimate_credits` from §8's
   *hold estimate* column (the settled price and the hold estimate are different numbers). These are
   **placeholders to be recalibrated in phase 0** — say so in a comment above the block. Follow
   design **§8.1**: the key is `(capability, model)`, so CHAT/IMAGE/VIDEO/AGENT get a capability
   default (`model_name NULL`) and **AUDIO gets one row per seeded model with no default** — TTS
   models bill `KILOCHAR`, STT models bill `AUDIO_MINUTE`. Do not add `unit` to the unique index.
5. **`credit_wallet` + `credit_ledger_entry`** — a starting `GRANT` for the two dev actors
   `550e8400-e29b-41d4-a716-446655440001` (admin) and `…0002` (user).
   Add the comment: *opaque ActorIds copied by value from the identity seed — no FK, no
   cross-context read; this seeds only this context's own database.*
6. **`billing_subscription`** — an `ACTIVE` row for each dev actor (`ultimate`, `premium`).

Keep every seed value consistent with design §8. If you change a number, change the design table in
the same commit — the two must not drift.

### `00-reset.sql`

Add, next to the existing identity/automation/knowledge blocks and in the same comment style:

```sql
-- Billing context: its own database — dropped wholesale.
DROP DATABASE IF EXISTS krizaka_billing_db WITH (FORCE);
DROP ROLE IF EXISTS krizaka_billing;
```

Also add the `70-billing.sql` line to the file-inventory comment at the top.

## §5 `krizaka-billing-api` — the Tier-1 contract

`pom.xml`: copy `krizaka/krizaka-build/krizaka-users/krizaka-users-api/pom.xml` verbatim, change
`artifactId`/`name`/`description`. **No dependency beyond `junit-jupiter` (test scope).**

Package root `com.krizaka.billing.domain`. Signatures — implement exactly these, with compact-
constructor validation (ERR-106) and a Javadoc on every public type:

```java
public enum BillableCapability { CHAT, IMAGE, AUDIO, VIDEO, AGENT }
public enum BillableUnit { KILOTOKEN, IMAGE_STEP, OUTPUT_SECOND, KILOCHAR, AUDIO_MINUTE, GPU_SECOND, CALL }
public enum EnforcementMode { OFF, DRY_RUN, ENFORCING }
public enum HoldStatus { ACTIVE, SETTLED, RELEASED, EXPIRED }

public record CreditHoldCommand(
    String actorId, BillableCapability capability, String modelName,
    String correlationId, String jobId, long estimatedCredits) { }
    // validate: actorId non-null non-blank · capability non-null
    //           correlationId non-null non-blank · estimatedCredits >= 0
    //           modelName and jobId are NULLABLE (chat has no jobId)

public record CreditHoldResponse(
    String holdId, boolean granted, boolean dryRun,
    long estimatedCredits, long balanceAfterHold, int pricebookVersion) { }
    // validate: holdId non-null non-blank · pricebookVersion >= 1

public record SettleCreditCommand(
    String holdId, BillableUnit unit, BigDecimal quantity, String idempotencyKey) { }
    // validate: all non-null · holdId/idempotencyKey non-blank
    //           quantity >= 0 (BigDecimal.ZERO comparison, not doubles)

public record EntitlementSnapshot(
    String actorId, String planKey, Map<String, String> entitlements,
    long availableCredits, Instant expiresAt) { }
    // validate: actorId/planKey non-null non-blank · entitlements non-null
    //           expiresAt non-null — a snapshot with no staleness bound cannot be cached safely
    // compact constructor: Map.copyOf(entitlements) — defensive copy, ERR-106
    // helper methods on the record (smart payload, ERR-127 — NOT a static util):
    //   boolean allows(String entitlementKey)
    //   int limit(String entitlementKey, int defaultValue)

public interface CreditAuthorizationClient {
  CreditHoldResponse hold(CreditHoldCommand command);   // throws InsufficientCreditsException
  void settle(SettleCreditCommand command);
  void release(String holdId, String reason);
}

public interface EntitlementProvider {
  EntitlementSnapshot forActor(String actorId);
}

public class InsufficientCreditsException extends RuntimeException { }
  // carries: required, available, capability — so the REST layer can build the
  // structured 402 of design §6.1 without re-deriving anything
```

Tests: one `*Test.java` per record (ERR-103), each asserting the **rejection** cases of its compact
constructor, not just the happy path. `EntitlementSnapshotTest` must assert the defensive copy
(mutating the source map after construction does not affect the record).

## §6 `.env` and `.env.example`

Append the two blocks of design §13.8, numbered to continue the existing sections. In
`.env.example` every secret is `CHANGE_ME`; in `.env` you may set the dev-local values **except**
`LAGO_API_KEY`, which does not exist until Lago has booted.

Defaults are **off**: `BILLING_ENABLED=false`, `LAGO_ENABLED=false`. A fresh clone must run without
billing.

## §7 `infra/docker-compose.yml`

Append the five Lago services of design §13.7 — `lago-db`, `lago-redis`, `lago-api`, `lago-worker`,
`lago-front` — each carrying `profiles: [billing]`, plus the `lago_data` volume.

Six things that are easy to get wrong — 4–6 were found the hard way on the first run:

1. **Ports are remapped** — Lago's API is `3000` internally (collides with `orazaka-web-client`) and
   its front `80`/`8080` (collides with the conversation-service). Publish on
   `127.0.0.1:8096` / `127.0.0.1:8097`, loopback-bound like every other service in this file.
2. **`lago_data` is a separate volume** from `pgvector_data`. Never put Lago inside
   `orazaka-db-vector`: `orazaka stop --purge` must not be able to delete financial records with dev
   data, and Lago's Rails migrations must not land in `infra/initdb/` (which `SqlBoundaryRules`
   scans as Orazaka-bounded-contexts-only).
3. **`profiles:` on every one of the five services.** If one is missed, `orazaka start` will try to
   start it and the default local loop breaks — that is the regression to check in §8.
4. **Compose does not read the repo-root `.env`.** The compose file lives in `infra/`, so that is
   Compose's project directory. Give every `${LAGO_*}` an explicit default (`${VAR:-}`) or the
   **default** `orazaka start` path prints `variable is not set` warnings on every run; and pass
   `--env-file .env` from the repo root when you actually start Lago.
5. **`RAILS_ENV: production` on the shared `&lago-api-env` anchor.** Without it `getlago/api:v1.45.1`
   boots Rails in `development`, whose gem group is not in the image — api and worker crash-loop with
   `LoadError: cannot load such file -- annotate_rb`.
6. **Lago v1.45.1 needs the `pg_partman` extension**, which `postgres:16-alpine` does not ship;
   `db:migrate` aborts and the API serves with no schema. Out of scope for tranche 1 — leave the
   pinned image, leave a comment. A partman-capable image is a phase-4 decision (design §16).

## §8 Acceptance gate — run these, in order

```bash
# 1. The contract module compiles and its tests pass
./mvnw -q -pl krizaka/krizaka-billing/krizaka-billing-api test

# 2. The whole reactor still builds (module registration is correct)
./mvnw -q -DskipTests install

# 3. The architecture rules and the data seam rule both still hold. SEAM-001 is NOT in core's
#    GovernanceTest — SqlBoundaryRules is exercised by SqlBoundaryTest in persistence, and that
#    is the one that actually reads 70-billing.sql. Run both.
./mvnw -q -pl engine/sovkora-engine-core -Dtest=GovernanceTest test
./mvnw -q -pl engine/sovkora-engine-persistence -Dtest=SqlBoundaryTest test

# 4. The default local loop is UNCHANGED — must start exactly the usual containers,
#    no Lago (this is the profiles: [billing] regression check)
orazaka stop --purge && orazaka start
docker ps --format '{{.Names}}'          # expect NO orazaka-lago-*

# 5. The billing database exists, is seeded, and the ledger is immutable
psql -h localhost -U krizaka_billing -d krizaka_billing_db -c "\dt"
psql -h localhost -U krizaka_billing -d krizaka_billing_db \
     -c "SELECT plan_key, monthly_credit_grant FROM billing_plan ORDER BY tier_rank;"
psql -h localhost -U krizaka_billing -d krizaka_billing_db \
     -c "UPDATE credit_ledger_entry SET amount = 999;"   # MUST fail with the trigger's exception

# 6. Lago starts only when asked. NOT via the CLI: `orazaka start` passes explicit service names,
#    so COMPOSE_PROFILES has nothing to select and this would silently start no Lago at all
#    (design §13.7). Invoke compose directly, from the repo root, with the root .env.
COMPOSE_PROFILES=billing docker compose -p orazaka --env-file .env \
  -f infra/docker-compose.yml up -d
docker ps --format '{{.Names}}'          # expect orazaka-lago-api / -front / -db / -redis / -worker

# 6b. Teardown of the opt-in stack is also profile-scoped (a bare `down` leaves them Exited):
COMPOSE_PROFILES=billing docker compose -p orazaka -f infra/docker-compose.yml down
```

Tranche 1 is done when **all six** pass. Step 5's failing `UPDATE` is a **success**: if it succeeds,
the trigger is missing and the ledger is not append-only.

## §9 Do NOT

- **Do not** create `krizaka-billing-service` in this tranche.
- **Do not** add any Spring dependency to `krizaka-billing-api`.
- **Do not** import `com.krizaka.orazaka.business.*` from the contract module (Tier-1 ↛ Tier-3).
- **Do not** add a foreign key from a billing table to any table outside `70-billing.sql`.
- **Do not** put a plan, price, credit rate or enforcement flag in any `application.yml`.
- **Do not** touch the user's uncommitted working-tree changes (there are modified files on `main`).
- **Do not** commit or push — the user reviews the diff and commits.
- **Do not** modify `docs/_generated/*` by hand.

## §10 Before declaring done

1. Run the `.agent/workflows/review_architect.md` gate.
2. `70-billing.sql` and `.env` changed ⇒ run `.agent/workflows/sync_documentation.md`.
3. Report: the file list you actually created/modified, the six gate results verbatim, and **any
   place where you had to deviate from `docs/BILLING_ARCHITECTURE.md`** — a deviation is a design
   bug to fix in the document, not a detail to absorb silently.

## §11 Next tranches (do not start without an explicit go)

- **Tranche 2** — `krizaka-billing-service` :8095. See design §19 and §5/§6/§10/§12.
- **Tranche 3** — consumer wiring, `NoOp`/HTTP adapters, `EntitlementInterceptor`, mode `DRY_RUN`
  end-to-end. See design §19 and §6.2/§6.3/§13.4.
