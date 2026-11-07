---
description: UNIFIED SURFACE — M1.5 (the dead orchestration kill-switch, the rule that makes the next one impossible, governance rules asserting over nothing, and the missing metering quantity invariant)
---

# Workflow: M1.5 — close the inert controls before they ship

Prior runs: [`implement_toolkit_kind.md`](implement_toolkit_kind.md) (M0.5 + M1) and its inventory,
[`docs/measurements/inert-controls-inventory.md`](../../docs/measurements/inert-controls-inventory.md).
Design context: [`docs/UNIFIED_PACK_SURFACE.md`](../../docs/UNIFIED_PACK_SURFACE.md) §5.

> **Why this run exists.** The M0.5 inventory was asked for findings and delivered them. The list has
> now reached the size where deferring it again means it ships. Four items — no more — and then M2.

## §0 Init

1. Load `AGENTS.md` — §0, §2, §3, §4 (config vs data), §9, §11.
2. Load `.agent/rules/configuration_standards.md`, `testing_standards.md`.
3. Read `docs/measurements/inert-controls-inventory.md` — **your own findings; do not rediscover them.**
4. Read, **before writing anything**:
   - the `SecurityProperties` fix that closed `disable-ai`, and the orchestration config type you
     found — **side by side. What they share is the anchor for §3.**
   - `build/sovkora-test-support/.../architecture/{GovernanceRules,SourceFileScanner,MeteringMarkerRules,PackCoherenceRules}.java`
     — the scanner you reuse and never rewrite
   - `krizaka/krizaka-billing/krizaka-billing-api/.../model/ConsumptionReport.java` — `quantityFor`,
     `megapixelSteps`, and **every caller that constructs a report**

## §1 Scope of THIS run — four items

1. §2 — the orchestration kill-switch (a live defect)
2. §3 — the rule that makes the next binding failure impossible
3. §4 — governance rules that assert over the empty set
4. §5 — `IMAGE_STEP`'s two causes, and the metering quantity invariant

**Out of scope, do NOT start** — the M2M JWT claim validation (it goes on the deployment block, §7),
Prettier configuration, the duplicated default in `PackCatalogEntry`, the stale schema description,
the marketplace entitlement band (that opens M2), anything in M2/M2.5/M3/M4/M5. If the four are green
and you have budget left, **stop and report**.

## §2 The orchestration kill-switch

`orazaka.core.orchestration.enabled` cannot be set to `false`. Verified independently: the property
appears in two `application.yml` files and in **zero** Java files — nothing reads it by name.

Fix it the way `disable-ai` was fixed, and **write the test the way you already wrote that one**:
against the real `application.yml` of both services, asserting `false` in produces `false` in the
bean. A unit test over a hand-built object would pass today and would have passed before the fix —
that is the whole lesson of these four findings.

Then answer one question in the report: **with the switch working, what does `false` actually do?**
A kill-switch that binds correctly and is read in one branch of five is inert in a fifth way. Trace
every read and say what is and is not disabled.

## §3 The rule — anchor it on the signature, not on my description

Two instances, one mechanism: **a configuration-bound type with more than one constructor loses its
binding silently, and the field default — always the permissive one — wins.** `disable-ai` and
orchestration are the same defect twice.

**Derive the rule's anchor from those two types, not from this paragraph.** Your first sweep keyed on
`@ConfigurationProperties` and missed the second instance, so the annotation is demonstrably the wrong
anchor. Put the two files side by side and answer: what is true of both, that a scanner can see, and
that is *not* true of the types you do not want flagged?

Requirements on whatever you land:

- It must fail on **both** known instances when they are reverted. Prove it on both, not one.
- **Plant a fresh violation** — a new config type with a convenience second constructor — watch it
  redden, revert. Two of this project's governance rules were themselves wrong; one anchored on a
  brace and matched a call site while validating a planted violation. A rule nobody has seen fail is
  a rule nobody has tested.
- False positives get a one-line justification in the rule, not a suppression list that grows.
- Reuse `SourceFileScanner`. Do not write a second scanner.

If, after looking at both types, you conclude no honest static anchor exists, **say so and propose the
runtime alternative instead** — a startup assertion that every declared `enabled`-style property
round-trips. Do not invent a fragile regex to satisfy this section.

## §4 Inert in a third way: asserting over nothing

`[PACK-001]` checked **zero packs** for several phases while reporting green. The M0.5 inventory could
not have found it: it swept for *not bound* and *not invoked*, and this rule was invoked. It iterated
an empty set.

So the definition widens to three: **not bound, not invoked, or invoked over nothing.** The third is
the dangerous one because it is green, and it lives in the layer meant to catch the other two.

1. Add the guard: a governance rule that completes having examined **zero** subjects **fails**, with a
   message naming the rule and the empty set. Make it a property of how the suites are written, not
   six separate assertions that can be forgotten on the seventh rule.
2. Apply it to every governance rule family and report what reddens. Expect more than `[PACK-001]`.
3. The six rules called by no suite: the generated registry displayed them, which **was** visibility
   and was not enough. Either wire them into a suite or delete them — a rule registered nowhere is
   documentation with a rule's name. Decide per rule and justify in one line.

## §5 `IMAGE_STEP` — the two causes and the missing invariant

Billing 20 steps regardless means `steps=30` under-bills and **`steps=10` bills double what was
done**. Nothing is deployed and nobody has been charged; that direction is still the one that matters.

1. Fix both causes. State each separately with its evidence — "two independent causes" is exactly the
   shape where fixing the visible one and declaring victory is the natural mistake.
2. Then the harder half. **`[BILL-001]` guards metering *paths* — that no `job.*` producer publishes
   without the marker. Nothing guards metering *quantities*.** Four billing defects in this project's
   history, all four found by reading, none by a rule. "What is billed equals what was done" has never
   been written as a checkable statement.

   Write it. The shape suggested by this defect: **no field of a `ConsumptionReport` may come from a
   constant or from the request's declared intent — only from what the executor reported doing.** A
   quantity taken from the caller's own request is the same class of error as a JWT scope the caller
   declares about itself. Check whether that is the right formulation against the four known defects
   before committing to it; if it does not cover them, find one that covers more and say which it
   misses.
3. Same proof obligation as §3: plant a violation, watch it redden, revert.

## §6 Non-negotiable constraints

| Rule | Applies here as |
|:---|:---|
| AGENTS.md §0 | 100% local. No CI, no cloud. |
| ERR-103 | One top-level type per file **+ one mirroring test file**. |
| **Test against the real thing** | Every fix here is a binding or a scan. Both classes of defect pass hand-built unit tests trivially. Bind from the real `application.yml`; scan the real tree. |
| **Every new rule is seen failing** | §3, §4 and §5 each require a planted violation, its output pasted, and its revert confirmed. No exceptions — this run is entirely about controls that claimed to work. |
| **No repairs in passing** | Not the M2M JWT, not Prettier, not the duplicated default, not the marketplace band. Record anything new; fix nothing outside §2–§5. |
| **Behaviour preservation** | Fixing the orchestration switch may change startup behaviour where the property is set to `false`. If any local config disables something that has silently been enabled all along, **say so loudly** — that is a behaviour change the switch was supposed to have been making for months. |

## §7 Goes on the deployment block, not in this run

The M2M JWT: `/intent/route` signs `AUTH_PASS` for scopes the caller declares about itself, the claim
validation is described in three places and implemented nowhere, and no service verifies the token
today. Inert, correctly assessed — **and the danger is the ordering.** The first service to implement
verification will make a self-declared-scope system look like working authentication.

Record it in the production-readiness audit as a blocking pre-deployment item with that reasoning.
Do not implement it here.

## §8 Before declaring done

1. `./mvnw -q verify` green, all `*IT` executed by failsafe. UI and CLI suites green.
2. Four planted violations (§3 twice — both known instances — §4, §5), each with output pasted and
   revert confirmed.
3. `spotless:check` still bound and still passing — M0.5's binding must not have been loosened to get
   this run green.
4. The §2 question answered: what `false` now actually disables.
5. Anything found and not fixed, with where it is.
6. Note whether `infra/migrations/2026-09-14-pack-kind.sql` has been applied locally; the services
   need it and the last run flagged it unapplied.
7. **Commit if green. Never push.**

## §9 Next — do not start

M2: the `orazaka-media` bundle and its six single-step blueprints, opening with the marketplace band
keyed on `kind` **and** the entitlement snapshot (three states: included, buyable, unavailable — keyed
on `kind` alone it tells an unentitled actor a pack is included, which is the mirror of the state M1
deleted). Then M2.5 harden the run path, then M3 close door 1.
