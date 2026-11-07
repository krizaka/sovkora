---
title: "Orazaka — Packs: the categorised marketplace"
description: "Overview and target architecture for the Pack catalogue: categories, packs, the Pack↔Studio relation, the admin authoring surface, and the regulatory class that a wellbeing pack forces the platform to introduce."
category: Architecture
order: 8
---

# Orazaka — Packs: the categorised marketplace

> **Your goal, restated.** An admin configures Packs. A user adds a Pack to their space. Every Pack
> belongs to a Category. Two categories: **Business** (Prospection, Validation) and **Life Style**
> (Therapy).
>
> **The finding that changes the plan.** Roughly 70% of this already exists and runs. `billing_pack`
> already carries a `category` column with a `CHECK (category IN ('BUSINESS','LIFESTYLE'))`;
> `PackController` + `PackSubscriptionController` already expose browse and add/remove;
> `PacksScreen.tsx` + `PackEditor.tsx` already give the admin an editor; `/packs` +
> `PackMarketplace` already give the user a marketplace. **One** pack is seeded.
>
> So this is not a feature to build. It is **one architectural decision to settle, three packs to
> author, and one new concept the Therapy pack forces on us.** Sections 3, 5 and 6 are the real work.

---

## 1. The overview you asked for — four layers, not two

Your mental model has two levels (Category → Pack). The platform has four. They are not redundant;
each answers a different question, and conflating any two is what would break the model later.

```
CATEGORY          BUSINESS                                  LIFE STYLE
                  │                                         │
PACK              ├─ Prospection ────┐                      └─ Therapy
  what you buy    ├─ Validation      │                          (price, entitlements, ownership)
  and own         └─ Studio Immo     │
                                     │
STUDIO            ┌────────────────────────────────┐
  what you run    │ outbound-prospection            │  ← installable, versioned, configurable
                  │ lead-enrichment                 │
                  │ follow-up-sequences             │
                  └────────────────────────────────┘
                                     │
BLUEPRINT         a versioned DAG of steps (semver, immutable once published)
                                     │
CAPABILITY        orazaka.core.chat.completion · …media.vision · …media.compose
  what executes   (rows in orazaka_capabilities, executed by job-service / media worker)
```

| Layer | Answers | Owner | Lifecycle |
|:---|:---|:---|:---|
| **Category** | *Which shelf?* | catalogue | closed vocabulary, ~5 values ever |
| **Pack** | *What do I buy, what does it unlock?* | billing (price) + catalogue (presentation) | admin-authored, priced, subscribed |
| **Studio** | *What do I run?* | studio | installed per actor, configured once, pinned to a version |
| **Blueprint** | *How does it run?* | studio | semver, immutable once `PUBLISHED` |
| **Capability** | *What actually executes?* | conversation/job | platform-owned, `feature_key` |

**The relation that matters: a Pack is a *bundle of Studios*, not a Studio.** Your own wording says
so — *"le pack prospection qui permet à un user d'avoir **des features** de prospection"*, plural.
The mechanism is already in place and needs no new code: `billing_pack_entitlement` lists N
entitlement keys, each Studio declares one `entitlement_key`, and `EntitlementSnapshot.allows(…)`
joins them. Adding a Studio to a Pack is **one row**.

### The user's two journeys

```
BUY                 /packs → PackMarketplace → "Add to my space"
                    → POST /api/v1/billing/pack-subscriptions/me/{packKey}
                    → billing_pack_subscription row → entitlements now include studio.*
                    → evt.billing.subscription.changed → studio caches invalidate

USE                 /studios → the Pack's Studios are no longer locked
                    → install → configure once (brand kit, tone) → run
                    → one credit hold per run, settled on measurement
```

The separation is deliberate: **owning a Pack is not installing a Studio.** A user may own the
Prospection pack and install only two of its four Studios. Collapsing the two would mean an unwanted
Studio clutters their space the day you add it to a pack they already bought.

---

## 2. What exists today, honestly

| Piece | State | Evidence |
|:---|:---|:---|
| `billing_pack` with `category` | ✅ exists | `70-billing.sql:60-69`, CHECK on `BUSINESS`/`LIFESTYLE` |
| `billing_pack_entitlement` | ✅ exists | `70-billing.sql` |
| `billing_pack_subscription` (add/remove to space) | ✅ exists | `70-billing.sql`, perpetual or periodic |
| Browse API | ✅ exists | `PackController` → `/api/v1/billing/packs` |
| Own/add/remove API, actor from token | ✅ exists | `PackSubscriptionController` → `/me/{packKey}` |
| Admin editor | ✅ exists | the operator console (`orazaka-web-client`) `PacksScreen.tsx`, `PackEditor.tsx` |
| User marketplace | ✅ exists | `web-client` `/packs`, `PackMarketplace.tsx` |
| Studio install/configure/run | ✅ exists | studio-service, 3 Studios seeded |
| **Packs actually seeded** | ⚠️ **one** | `realestate-studio` only — now a `pack` row in `80-studio.sql` |
| **Pack presentation data** | ✅ **done** (phase 1, ADR-036) | `pack` + `pack_i18n`: `icon_key`, `hero_asset_id`, `tagline`, `description`, fr/en |
| **Category as a browsable entity** | ✅ **done** (phase 1, ADR-036) | `pack_category` + `pack_category_i18n`: label, icon, sort order, fr/en; served by `/api/v1/studios/packs/categories` |
| **Prospection / Validation / Therapy packs** | ❌ missing | — |
| **Regulatory classification** | ❌ missing | §6 — the Therapy pack cannot ship without it |

**Read the gap correctly.** The plumbing is done. What is missing is *content* (three packs and their
Studios) plus *two structural gaps* that will hurt precisely when you add content: a Pack has no
presentation data, and a Category has no identity.

> **Update — phases 1 and 2 are implemented (ADR-036).** Both structural gaps above are closed: the
> catalogue lives in the studio context (`pack`, `pack_i18n`, `pack_studio`, `pack_category`,
> `pack_category_i18n`), `billing_pack` narrowed to a price tag, `/api/v1/studios/packs` serves the
> localised marketplace, and the four §4 invariants are enforced — invariant #1 in `Pack`'s compact
> constructor, #2 and #3 by `PackCoherenceRules` [PACK-001] over the seed files, #4 by a SQL `CHECK`
> plus the `RegulatoryClass` enum. The rows are kept rather than deleted so the reasoning that
> produced them stays readable.
>
> Two things this document got wrong, corrected here rather than absorbed silently:
>
> - **§4 gives `pack` no base label.** `studio` carries both a base `label` and `studio_i18n`, so
>   "fall back to the base row" is meaningful there; for `pack` every string lives in `pack_i18n`
>   and there is nothing to fall back *to*. The read path therefore falls back to the pack's
>   lowest-sorting translation, then to the key. Adding a base `label` column would have been the
>   other repair and was rejected: it would give one string two homes and re-create the drift the
>   split removes.
> - **`pack_studio` cannot sit with the other pack tables.** §4 lists it in the studio-context block
>   and the phase-1 workflow says to place the catalogue immediately before `CREATE TABLE studio`.
>   It references `studio(studio_key)`, so Postgres requires it *after* that table. A Pack is
>   browsed before a Studio is installed but declared after one; the file says so where it happens.

---

## 3. The one decision to settle first: where the Pack catalogue lives

Today `billing_pack` holds `label`, `category` and `profession` — **presentation data inside the
money context**. The Studio context, by contrast, has `studio_i18n`, `icon_key`, `tagline` and
`hero_asset_id`.

Consequence, visible the moment you author three packs: a Pack card in the marketplace is poorer
than a Studio card. No icon, no description, no French/English label. The reflex will be to add
`icon_key`, `tagline`, `hero_asset_id` and a `billing_pack_i18n` table to the billing database — and
then you own **two divergent i18n tables** and a money service that is also a CMS.

### Recommended — Option B: split by question

> **Billing answers "what does it cost and what does it grant".
> The catalogue answers "what is it and what does it do".**

- **billing keeps**: `pack_key`, `price_cents`, `included_credits`, `external_plan_code`,
  `is_active`, `billing_pack_entitlement`, `billing_pack_subscription`. Nothing a marketer edits.
- **studio-service gains** the catalogue: `pack`, `pack_i18n`, `pack_studio` (the join),
  `pack_category`, `pack_category_i18n`. It already owns `studio_i18n` and the browse path — one
  i18n mechanism, one marketplace query, one admin console.
- `pack_key` stays an **opaque** string on both sides. No cross-context FK — [SEAM-001] holds.

**Why not the alternatives.** *Option A (enrich billing)* is faster this week and wrong by the third
pack: the money service accumulates marketing columns and a second i18n table, and every marketing
copy change becomes a deploy of the service that holds the credit ledger. *Option C (a third
"catalogue" context)* is correct on paper and unjustifiable in practice — it would be a service whose
only job is to join two tables, and AGENTS.md §2 names a growing `sovkora-libs` as the primary health
gauge; a growing service count is the same disease.

**Migration cost is near zero today** — one seeded pack. It will not be near zero after three.

### Category: a table, not a CHECK constraint

The current `CHECK (category IN ('BUSINESS','LIFESTYLE'))` is defended in a comment as deliberate:
*"a shelf nobody can name in the front-end is a shelf nobody browses."* The reasoning is sound; the
conclusion no longer holds once a category needs a **French label, an icon, a sort order and a
description** — which "Life Style" does the moment it sits next to "Business" in a marketplace.

Move it to `pack_category` + `pack_category_i18n` in the studio context. Keep the closed spirit:
categories stay admin-only, ~5 rows ever, and a governance test asserts that every `pack.category_key`
resolves. You lose nothing and gain a browsable, translatable shelf.

---

## 4. Target model

```sql
-- ── studio context (80-studio.sql) ─────────────────────────────────────────
CREATE TABLE pack_category (
    category_key VARCHAR(30) PRIMARY KEY,      -- business | lifestyle
    icon_key     VARCHAR(60) NOT NULL,
    sort_weight  INT NOT NULL DEFAULT 0,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE pack_category_i18n (
    category_key VARCHAR(30) NOT NULL REFERENCES pack_category(category_key) ON DELETE CASCADE,
    locale       VARCHAR(10) NOT NULL,
    label        VARCHAR(80) NOT NULL,
    description  TEXT,
    PRIMARY KEY (category_key, locale)
);

CREATE TABLE pack (
    pack_key         VARCHAR(50) PRIMARY KEY,   -- OPAQUE, mirrored in billing_pack — no FK
    category_key     VARCHAR(30) NOT NULL REFERENCES pack_category(category_key),
    icon_key         VARCHAR(60) NOT NULL,
    hero_asset_id    VARCHAR(255),
    regulatory_class VARCHAR(20) NOT NULL DEFAULT 'STANDARD',   -- §6
    kind             VARCHAR(20) NOT NULL DEFAULT 'VERTICAL',   -- §4.1, ADR-061
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',      -- DRAFT|PUBLISHED|WITHDRAWN
    sort_weight      INT NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_pack_regulatory CHECK (regulatory_class IN ('STANDARD','SENSITIVE','REGULATED'))
);
CREATE TABLE pack_i18n (
    pack_key VARCHAR(50) NOT NULL REFERENCES pack(pack_key) ON DELETE CASCADE,
    locale   VARCHAR(10) NOT NULL,
    label    VARCHAR(120) NOT NULL,
    tagline  VARCHAR(255),
    description TEXT,
    PRIMARY KEY (pack_key, locale)
);

-- The bundle. A Studio may belong to several Packs; a Pack holds several Studios.
CREATE TABLE pack_studio (
    pack_key    VARCHAR(50) NOT NULL REFERENCES pack(pack_key) ON DELETE CASCADE,
    studio_key  VARCHAR(60) NOT NULL REFERENCES studio(studio_key) ON DELETE CASCADE,
    sort_weight INT NOT NULL DEFAULT 0,
    PRIMARY KEY (pack_key, studio_key)
);

-- ── billing context (70-billing.sql) — SHRINKS ─────────────────────────────
-- DROP COLUMN label, category, profession.  billing_pack becomes a price tag:
--   pack_key · price_cents · included_credits · external_plan_code · is_active
```

**Invariants** (each mirrored in the record's compact constructor, per ERR-106):

1. A `PUBLISHED` pack has ≥1 `pack_studio` row. An empty shelf item is a support ticket.
2. Every Studio reachable through a `PUBLISHED` pack must itself be `PUBLISHED` — otherwise a user
   buys access to something that cannot run. **Fitness function**, not a review item.
3. Every `pack_studio.studio_key` implies a `billing_pack_entitlement` row granting that Studio's
   `entitlement_key`. Derive it rather than hand-maintaining it: a governance test that finds a
   Studio in a Pack with no matching entitlement row **fails the build**. This is the single most
   likely production defect in this feature — a user pays and stays locked out.
4. `pack.regulatory_class = REGULATED` ⇒ the publish path requires the §6 checklist.
5. `pack.kind = TOOLKIT` ⇒ not `REGULATED`, no `consent_version`, no `safety`, and a catalog entry
   — enforced by `ck_pack_toolkit_not_regulated` and `ck_pack_toolkit_no_installation_controls`,
   mirrored in `Pack` and `PackBundle`, and read over the manifests by [PACK-001] (ADR-061).

### 4.1 `kind` — the third classification, orthogonal to the other two

| Classification | Values | Answers | Lives |
|:---|:---|:---|:---|
| `tier` | DATA · CAPABILITY · WORKER | what the pack contributes to the platform | `pack.yaml` only |
| `kind` | VERTICAL · TOOLKIT | how it reaches the user | `pack.kind` |
| `regulatory_class` | STANDARD · SENSITIVE · REGULATED | what controls it forces | `pack.regulatory_class` |

`category` answers **for whom**; `kind` answers **what sort of thing**. A **VERTICAL** is chosen and
installed, and its installation is a row that pins a blueprint version and records consent. A
**TOOLKIT** is platform capability an entitled actor simply has: its installation is derived from
the entitlement and never stored, so "the plan grants it but it is not installed" is not a state it
can be in. With no row there is no pin — a TOOLKIT run resolves the latest published version once,
at start, and records it on `studio_run.blueprint_version`.

None of the three is derived from another. A TOOLKIT may be Tier-D, -C or -W, and STANDARD or
SENSITIVE. The one forbidden pairing, TOOLKIT with REGULATED, is forbidden by where consent is
recorded — not because a kind implies a class. Nor is `kind` read from a key's spelling:
`echo-toolkit` is a VERTICAL.

---

## 5. The three packs

### 5.1 `prospection` — *"Prospection"* · BUSINESS · reuse rate: high

| | |
|:---|:---|
| Studios | `outbound-prospection` (**exists, PUBLISHED**), `lead-research`, `followup-sequences` |
| New capabilities | none |
| Step kinds used | `KNOWLEDGE` (the actor's own case studies) · `CAPABILITY` (chat fan-out) · `APPROVAL` · `CONNECTOR` (CRM/email) |
| Pricing | `INCLUDED` on the top plan, `PAID` otherwise |

This one is nearly free. `outbound-prospection` already exists and already exercises the three step
kinds the media Studios do not. `lead-research` and `followup-sequences` are blueprint rows over
existing capabilities plus the `connector_credentials` table that already holds CRM secrets.

**Ship this pack first**, precisely because it needs no new capability: a failure is then
unambiguously a Pack-layer bug, not an execution bug. Same reasoning that made `trade-showcase` the
Studio phase-3 fixture.

### 5.2 `document-validation` — *"Validation documentaire"* · BUSINESS · reuse rate: medium

*"Valider des documents selon des règles et contraintes."*

The platform already has the engine for this, and it is not the LLM. `validation_pipeline_configs`
(the seeded four-tier matrix `STRUCTURAL_A` → `SANDBOX_B` → …) and `ClosedLoopValidationInterceptor`
were built to check a model's output against a schema, a sandbox and a debate. A document-validation
pack is **the same machinery pointed at a user's document instead of a model's answer**.

| | |
|:---|:---|
| Studios | `contract-review`, `compliance-check`, `form-completeness` |
| New capability | `orazaka.core.document.validate` — one row, one strategy |
| New step kind | `RULESET` — evaluates a stored rule set and returns findings, never prose |
| New table (studio ctx) | `validation_ruleset` + `validation_rule` — admin-authored, versioned like a blueprint |

**The design rule that keeps this honest**: a rule is **deterministic first, LLM second**. Structural
checks (a required clause is present, a date is in range, a total matches its lines) run as code and
are auditable. The model is used only where judgement is genuinely required (is this indemnity clause
unusual?), and its verdict is always labelled as an opinion with its evidence quoted. A validation
product whose answers cannot be explained is a liability, not a feature — the user will forward your
output to their counterparty.

Store findings as `(rule_id, severity, verdict, evidence_span, explanation)`. Never as a paragraph.

### 5.3 `wellbeing` — *"Bien-être"* (your "thérapie") · LIFE STYLE · **read §6 before building**

This pack is not architecturally harder than the others. It is *legally and ethically* harder, by a
wide margin, and that difference has to be expressed in the architecture rather than in a disclaimer.

| | |
|:---|:---|
| Studios | `guided-journaling`, `session-preparation`, `mood-tracking` |
| New capabilities | none — it is chat + structured prompts + retrieval |
| New requirements | consent gate · crisis interceptor · sensitive data class · shortened retention · human-escalation path · audit trail |
| `regulatory_class` | `REGULATED` |

---

## 6. The concept the wellbeing pack forces: `regulatory_class`

I would be doing the job badly if I handed you a schema for a "pack thérapie" without this section.

### 6.1 Why "thérapie" is the wrong product name

Naming decides regulation. A system **intended for a medical purpose** — diagnosing, treating,
alleviating a condition — is a medical device under EU MDR, with conformity assessment attached.
Under the EU AI Act, systems inferring emotions or intended for health purposes attract additional
obligations. And a system marketed as *therapy* invites a duty-of-care argument the moment a user is
harmed, regardless of what your terms of service say.

The same underlying product, named and scoped as **guided journaling · session preparation · mood
tracking**, makes no medical claim and stays out of that regime. This is not wordplay: it changes
what the model is allowed to say, and the architecture below enforces the difference rather than
hoping the prompt holds.

**Recommendation**: name the pack **Bien-être**, and let its Studios say what they do.
Never *diagnose*, never *treat*, never *replace a professional*. Position it as the thing a user
brings **to** their therapist, not instead of one — which is also the more defensible product.

### 6.2 What `REGULATED` must switch on, mechanically

| Control | Mechanism |
|:---|:---|
| **Explicit consent** | Install is blocked until an explicit, versioned consent is recorded (GDPR Art. 9 / Loi 25 sensitive data). Store `consent_version` on the installation; a new version re-prompts. |
| **Crisis interceptor** | A **non-bypassable** `SafetyInterceptor` in `orazaka-interceptors/…/validation/`, forced on for `REGULATED` packs via an `interceptor_policy` predicate. It detects crisis signals, short-circuits the pipeline, and returns a fixed, human-reviewed response with regional resources — never a generated one. `isAiDependent() = false`: it must survive the `disable-ai` kill-switch. |
| **Scope guard** | A second interceptor refusing diagnosis, medication and dosage questions with a fixed redirect. Prompt instructions are not a control; an interceptor is. |
| **Data class** | `data_class = SENSITIVE` on runs and chat sessions ⇒ encryption at rest (audit finding #13 becomes a **blocker** for this pack, not a wave-4 item), no analytics export, no training use, ever. |
| **Retention** | Default 30 days instead of the platform default, user-configurable downward, hard-deleted by `RetentionSweeper`. |
| **Audit trail** | Every run of a `REGULATED` pack writes an append-only audit row. Same trigger construction as `credit_ledger_entry`. |
| **Publish gate** | `BlueprintPublishService` refuses to publish a `REGULATED` pack's blueprint unless the safety interceptors are active in `interceptor_policy` and a `safety_review_ref` is recorded. The gate is code, not a checklist someone remembers. |
| **Minors** | Age attestation at install; the pack is unavailable to accounts flagged as minors. |

### 6.3 Why generalise instead of special-casing therapy

Because the next pack has the same shape. A legal-advice pack, a medical-summary pack, a financial-
advice pack — each needs consent, a scope guard, sensitive retention and an audit trail. Building
`regulatory_class` once means the fourth such pack is a **row**, which is the promise the whole Studio
architecture makes. Special-casing therapy means the fourth one is a rewrite under time pressure.

**`SENSITIVE`** (personal but not health — e.g. a legal-drafting pack) gets the data class, retention
and audit controls without the consent gate and crisis interceptor. **`STANDARD`** is every pack you
have today and changes nothing.

> **Amended (2026-09-06) — SENSITIVE carries FOUR controls, not three.** The sentence above listed
> data class, retention and audit, and put the scope guard in the `REGULATED` column alone. That was
> wrong in the direction that matters: the example it gives for `SENSITIVE` is a legal-drafting pack,
> and a legal-drafting pack with no scope guard is one that answers legal questions. The guard is the
> only one of the four that acts while the user is waiting, and it is the cheapest of them — an
> interceptor reading a declaration, no consent flow and no reviewed crisis model.
>
> What stays with `REGULATED` and is **not implemented**: versioned consent, the crisis
> `SafetyInterceptor`, and the minors gate. ADR-051 implements
> the four and says so; §6.2's table above still describes `REGULATED` in full.
>
> **Amended (2026-09-10) — REGULATED is implemented, with one half of one row missing.**
> ADR-055 ships versioned consent
> (blocking, `451` with the statement) and the non-bypassable `SafetyInterceptor` (first in the
> chain, `isAiDependent() = false`, fixed reviewed response with a sourced regional resource).
>
> The **minors** row above asks for two things and only one exists. "Age attestation at install"
> ships and blocks. "Unavailable to accounts flagged as minors" does **not**: the identity context
> carries no date of birth, no age and no minority marker, and nothing populates one — so there is
> no flag to read. Adding a birth date to every account for one pack is data minimisation
> backwards, and a field that always reads false is a control that never fires. Recorded as absent
> rather than implemented as decoration.
>
> The **publish gate** row is also narrower than written: the gate refuses a REGULATED bundle
> without its consent and safety declarations, and `safetyReviewRef` is required and recorded. It
> does not check `interceptor_policy`, because the crisis guard is not in that table at all — it is
> a `CORE_INTERCEPTOR_KEY`, which is stronger: no row can switch it off (ADR-051 §3, ADR-055 §4).

---

## 7. Plan

Each phase ends green (build + every `GovernanceTest`) and is an independent checkpoint. Phases 1–2
are structural and must precede content; authoring three packs on today's schema would make the
migration three times more expensive.

| Phase | Scope | Done when |
|:---|:---|:---|
| **1 — Catalogue split** | `pack_category`(+i18n), `pack`(+i18n), `pack_studio` in `80-studio.sql`; `billing_pack` loses `label`/`category`/`profession`; `PackCatalogController` in studio-service; billing's `PackController` narrows to price+entitlement | `/api/v1/studios/packs?locale=fr` returns the seeded pack with a French label and an icon; billing still prices it |
| **2 — Invariants & fitness functions** | The four §4 invariants as compact-constructor checks + governance tests, notably *"a Studio in a PUBLISHED pack has a matching entitlement row"* | A pack with a Studio and no entitlement row **fails the build** |
| **3 — Prospection pack** | `prospection` pack row, 2 new blueprints (`lead-research`, `followup-sequences`), `pack_studio` rows, entitlements, i18n | A user adds the pack and runs `outbound-prospection` unlocked |
| **4 — Admin console** | Extend `PacksScreen`/`PackEditor`: category picker, i18n editor, Studio multi-select, live entitlement preview, publish gate | An admin creates a pack end-to-end with **zero SQL** |
| **5 — Validation pack** | `orazaka.core.document.validate` capability + strategy, `RULESET` step kind, `validation_ruleset`/`validation_rule`, 3 blueprints | A contract is validated and each finding cites its rule and its evidence span |
| **6 — `regulatory_class`** | The seven §6.2 controls: consent gate, `SafetyInterceptor`, scope guard, data class, retention, audit trail, publish gate | A `REGULATED` pack cannot be published with the safety interceptors inactive |
| **7 — Wellbeing pack** | 3 blueprints, `REGULATED`, i18n, crisis resources per region, safety review recorded | Adversarial safety suite passes; encryption at rest is on for `SENSITIVE` runs |

**Dependency to respect**: phase 6 gates phase 7 absolutely. And phase 7 depends on audit finding #13
(encryption at rest) being closed — for this pack it is a blocker, not a hardening item. Sequence
wave 4 of the audit before phase 7, or ship the wellbeing pack last.

**If you want something visible this week**: phases 1 + 3. That is a real categorised marketplace with
two populated categories, on existing execution machinery.

---

## 8. Manifest

```
# Phase 1–2
MODIFY  infra/initdb/80-studio.sql            (+ pack_category, pack_category_i18n, pack, pack_i18n, pack_studio, seeds)
MODIFY  infra/initdb/70-billing.sql           (− label/category/profession; billing_pack becomes a price tag)
CREATE  services/studio/sovkora-studio-api/.../domain/model/{Pack,PackCategory,PackSummary,RegulatoryClass}.java
CREATE  services/studio/sovkora-studio-service/.../application/service/PackCatalogService.java
CREATE  .../infrastructure/adapter/rest/PackCatalogController.java          (/api/v1/studios/packs)
CREATE  .../infrastructure/adapter/persistence/JdbcPackRepositoryAdapter.java
MODIFY  krizaka/krizaka-billing/krizaka-billing-service/.../PackController.java  (narrow to price + entitlements)
MODIFY  build/sovkora-test-support/.../GovernanceRules.java          (+ pack/entitlement coherence rule)
MODIFY  <8 services> .../<Service>GovernanceTest.java

# Phase 3 + 5 + 7 — content
MODIFY  infra/initdb/80-studio.sql            (studio + studio_blueprint + pack_studio rows per pack)
MODIFY  infra/initdb/70-billing.sql           (billing_pack + billing_pack_entitlement rows)
MODIFY  infra/initdb/30-jobs-config.sql       (+ orazaka.core.document.validate, phase 5)
CREATE  engine/sovkora-engine-business/src/main/resources/prompts/pack/**    (prompt markdown)

# Phase 4 — admin
MODIFY  apps/web/src/console/features/billing/components/{PacksScreen,PackEditor}.tsx
CREATE  apps/web/src/console/features/catalogue/components/{CategoryEditor,PackStudioPicker,PackI18nEditor}.tsx

# Phase 6 — regulatory
CREATE  engine/sovkora-engine-interceptors/.../interceptor/validation/SafetyInterceptor.java
CREATE  .../interceptor/validation/ScopeGuardInterceptor.java
MODIFY  infra/initdb/60-governance.sql        (+ activation predicates for REGULATED packs)
MODIFY  infra/initdb/80-studio.sql            (+ consent_version, data_class, pack_run_audit + its trigger)
MODIFY  .../application/service/{StudioInstallationService,BlueprintPublishService}.java
CREATE  ADR-036 (internal)

# UI
MODIFY  apps/web/src/features/billing/components/PackMarketplace.tsx  (group by category)
CREATE  apps/web/src/features/pack/components/{CategoryShelf,PackCard,ConsentDialog}.tsx
MODIFY  orazaka-apps/ui/orazaka-ui-kit/orazaka-shared/src/schemas/pack.ts
```

---

## Related documentation

- [Studio architecture](STUDIO_ARCHITECTURE.md) · ADR-034
- [Billing architecture](BILLING_ARCHITECTURE.md) · ADR-033
- [Production readiness audit](PRODUCTION_READINESS_AUDIT.md) — finding #13 gates phase 7
- [Governance contract](../AGENTS.md) · [Security standards](../.agent/rules/security_standards.md)
