---
description: UNIFIED SURFACE — M1.6 (close the inline-media chat bypass, extend the divergence contract to video, record the inert-control principle)
---

# Workflow: M1.6 — the bypass that is wider than door 1

Prior run: [`remediate_inert_controls.md`](remediate_inert_controls.md) and ADR-062.
Findings being closed: `PRODUCTION_READINESS_AUDIT.md` **#21** (the media-turn bypass) and **#20**
(video composition bills intended duration).

> **Why this runs before M2.** M3 exists to close a governance bypass in which a direct capability
> invocation produces an `orazaka_jobs` row instead of a `studio_run` row — no data class, no
> retention, no audit, no scope guard. #21 is the same bypass, wider, and **worse**: door 1 at least
> left a job row and took a credit hold. This path leaves nothing and takes nothing.

## §0 Init

1. Load `AGENTS.md` — §0, §2, §6, §7 (as corrected in the last run), §9, §11, §12.
2. Read ADR-062 §2 and audit findings #20, #21.
3. Read, **before writing anything**:
   - `engine/sovkora-engine-core/.../pipeline/EnginePipelineBridge.java` — `compileContext`,
     lines ~86–121. **Read the whole method before touching the branch.**
   - `Base64MediaExtractor` — specifically what `cleanedQuery()` returns
   - `MessageCompiler.compile(request, promptText, mediaResult, hasMedia)`
   - the scope-guard interceptor and how `orazaka.guard.subject` is populated (ADR-051)
   - the divergence contract you wrote in the last run, and the image fixture it uses

## §1 Scope of THIS run

1. §2 — close #21.
2. §3 — extend the divergence contract to video, which closes #20 by producing the instrument.
3. §4 — one paragraph into `AGENTS.md`, and one assumption written down.

**Out of scope, do NOT start** — the `orazaka-media` bundle or any blueprint (M2), the marketplace
entitlement band (M2's opener), the outbox on step dispatch / lanes / per-lane ceiling (M2.5),
deleting any controller (M3), the M2M JWT (deployment block). If the three are green and you have
budget left, **stop and report**.

## §2 #21 — the media turn

### §2.1 Exposure is established. Do not re-investigate it.

`Base64MediaExtractor.extract(cleanedRawQuery)` reads the **prompt string**. `hasMedia` is therefore
true for any caller who puts a base64 data URL in the text they send. No special endpoint, no upload,
no client capability. The exposure is the chat API, and the last run's "exposure not established"
should be corrected in the audit to say so.

### §2.2 Start with the question, not the fix

**Why does the branch exist?** Note what the code already does before it:

```java
String queryForPipeline = mediaResult.cleanedQuery();   // media already stripped
...
if (!hasMedia) {
    pipelineContext = ... p.process(queryForPipeline, request.context()) ...
}
```

The pipeline would receive the **cleaned** query — the base64 is gone by then. So the obvious
justification for the skip ("interceptors choke on a megabyte of base64") does not survive reading
the two lines above it. **Establish the real reason before changing anything.** Check the history of
that branch. If it is vestigial, say so; if an interceptor genuinely fails on a media turn, **that is
the defect**, and fixing the interceptor is the fix — not preserving the bypass around it.

### §2.3 The subtlety that would produce a fix that is not one

Running the pipeline on the cleaned text is **not sufficient**, and stopping there would close the
audit finding while leaving the hole.

The scope guard reads its subject from the request's inputs (ADR-051). On a media turn, the *subject
is the image*. A lease photographed and sent with the prompt "what do you think?" passes a text-only
scope guard trivially — the guard runs, reports green, and examined nothing that mattered. **That is
the `[PACK-001]` shape again: a control invoked over the wrong set.**

So whatever you land must make media presence visible to the pipeline, and the guard must be able to
act on it. A guard that cannot see that an image is in the room is not guarding a media turn.

### §2.4 The end state

Two candidates. **Pick (a) unless the investigation of §2.2 forces otherwise**, and record why:

- **(a) The pipeline runs for every turn**, media included, with media presence passed as context.
  Minimal, restores all four controls, keeps a working feature working.
- **(b) Media turns are refused on the chat endpoint** and must go through the run path. Cleaner, and
  it is where M3 is heading — but it is a product change, it removes something that works today, and
  **that decision belongs to the media pack design, not to a remediation run.** Do not pre-empt it.

Whichever you land, four things must be true afterwards for a turn carrying an image: the
orchestration switch is honoured, `disable-ai` is honoured, the scope and crisis guards run **with the
media in their subject**, and an entitlement check takes a credit hold.

### §2.5 The billing consequence, which is a finding in its own right

The bypassed entitlement check means **media chat turns have never been billed**. Vision inference is
not free. Count it: that is the sixth billing defect, and the second of the "quantities" class the
last run opened. Say so explicitly in the report rather than letting it disappear into the fix.

### §2.6 Proof

A test that sends a chat turn whose prompt contains an inline data URL and asserts, separately:
the switch is honoured, the guards ran and saw the media, a hold was taken. Then **remove the fix,
watch each assertion fail, restore.** Not one assertion covering four things — four, because four
different controls were bypassed and a single combined assertion can pass for the wrong reason.

## §3 #20 — extend the divergence contract to video

9.00 s billed for a 2.02 s file. Same defect as the image dimensions the contract already caught
(`-shortest` truncates the output to the shorter audio track): **billing the intention rather than the
act.** The contract found the image case the day it existed and does not exist for video.

So the deliverable is **coverage, not a patch**:

1. Extend the divergence contract to video composition, using `-shortest` with a deliberately short
   audio track as the fixture — the case that produced 9.00 vs 2.02.
2. The billed duration must be measured from the **produced file**, never from the requested
   duration. Make this consistent with the image path, which now measures the returned image.
3. Report what else the contract does not cover. Two media types were checked by hand and one was
   wrong; the third should be found by the contract, not by the next report.

## §4 Two things to write down

**Into `AGENTS.md`**, as a principle, in the project's existing voice:

> **Repairing an inert control is a change, not a fix.** A control that has never been active has a
> branch that has never executed. Trace that branch before making it reachable. Three of the inert
> controls found in this codebase had an unexecuted branch behind them, and in one case — the
> orchestration kill-switch (ADR-062) — repairing the binding alone would have created the
> vulnerability the switch was believed to prevent.

**Into the metering documentation**, as an assumption rather than a settled matter: `steps` is not
carried by any produced image, so it still comes from configuration. The quantity is correct only
while the configuration describes what the executor actually ran. That is an assumption, not an
invariant, and it should read as one.

## §5 Non-negotiable constraints

| Rule | Applies here as |
|:---|:---|
| AGENTS.md §0 | 100% local. No CI, no cloud. |
| AGENTS.md §12 | The engine holds the mechanism, never the subject. Making media visible to the pipeline must not put media semantics into engine code. |
| ERR-103 | One top-level type per file **+ one mirroring test file**. |
| **Four assertions, not one** | §2.6. Four controls were bypassed. |
| **No bypass preserved as a fallback** | If an interceptor fails on a media turn, fix the interceptor. A `catch` that restores the old behaviour is the same hole wearing a hat — the phase A rule, applied here. |
| **No repairs in passing** | Record anything new. Do not open M2, M2.5 or M3. |

## §6 Before declaring done

1. `./mvnw -q verify` green with all `*IT`; UI and CLI suites green; `spotless:check` still bound.
2. `[CFG-001]`, the empty-set meta-rule and the divergence contract all still active and passing —
   this run must not have loosened last run's controls to go green.
3. The §2.2 answer: why the branch existed, with evidence, and whether it was vestigial.
4. The §2.6 removal test: four assertions, each seen failing, restored.
5. Audit #20 and #21 updated — #21's "exposure not established" corrected to what §2.1 establishes.
6. The unbilled-media-turns finding stated as its own line item, not folded into the fix.
7. `infra/migrations/2026-09-14-pack-kind.sql` applied after `orazaka start`, or explicitly reported
   as still unapplied.
8. **Commit if green. Never push.**

## §7 Next — do not start

M2: the `orazaka-media` bundle and its six single-step blueprints, opening with the marketplace band
keyed on `kind` **and** the entitlement snapshot. Then M2.5 harden the run path (outbox on dispatch,
lanes not priorities, per-lane ceiling). Then M3 close door 1.
