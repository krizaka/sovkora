<!-- krizaka-header -->
<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/sovkora-dark.svg">
  <img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/sovkora-light.svg" alt="Sovkora" width="96">
</picture>

# Sovkora AI Engine

**The AI that never leaves home.**

The Sovkora cognitive SDK: AiClient & provider mesh (core), the interceptor pipeline, tools (MCP · RAG · sandbox), use-case orchestration (business), application persistence and asset encryption.

[![CI](https://github.com/krizaka/sovkora-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/krizaka/sovkora-engine/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/krizaka/sovkora-engine/badge)](https://scorecard.dev/viewer/?uri=github.com/krizaka/sovkora-engine)
[![License: FSL-1.1-ALv2](https://img.shields.io/badge/license-FSL--1.1--ALv2-blue.svg)](LICENSE.md)
[![Sovkora](https://img.shields.io/badge/part%20of-Sovkora-f67e23)](https://github.com/krizaka/sovkora#repositories)
[![Docs](https://img.shields.io/badge/docs-krizaka.com-f67e23)](https://www.krizaka.com/en/products/sovkora)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-ai-engine&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-ai-engine) [![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-ai-engine&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-ai-engine) [![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-ai-engine&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-ai-engine) [![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-ai-engine&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-ai-engine) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-ai-engine&metric=coverage)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-ai-engine)

[Documentation](https://www.krizaka.com/en/products/sovkora) · [Website](https://www.krizaka.com) · [Krizaka on GitHub](https://github.com/krizaka)

</div>
<!-- /krizaka-header -->

**Layer:** Sovkora AI engine · **Version:** `1.0.0-SNAPSHOT` · **License:** FSL-1.1-ALv2 ·
part of the [Sovkora platform](https://github.com/krizaka/sovkora) by [Krizaka](https://krizaka.com)

## What it provides

| Module | Tier | Role |
|:---|:---|:---|
| `sovkora-core` | SDK | Cognitive engine: `AiClient`, provider mesh (DB-driven routing), agent loop, interceptor contract. Web/DB-agnostic. |
| `sovkora-adapter-spring-ai` | SDK | The only module meant to depend on Spring AI (ADR-092 §4): the tool registry and structured-output client on top of it; the other ports follow slice by slice. |
| `sovkora-architecture` | — | No main code: the boundary rules (no Spring AI outside the adapter, no Spring in `core`/`business`) behind a ratchet, `architecture-ratchet.json`. |
| `sovkora-interceptors` | SDK | Ordered, short-circuitable pipeline: security, token, context, translation, enrichment, validation, governance. |
| `sovkora-tools` | SDK | MCP client, RAG, sandbox, law retrieval, the `CALL` meters (research, external checks). |
| `sovkora-business` | Owned | App factory: `Intention` → `UseCase`, deterministic DAG or saga. |
| `sovkora-persistence-app` | Owned | CQRS persistence of the AI state (chat, jobs, models, configs, outbox). |
| `sovkora-persistence-bridge` | Owned | Adapters from core outbound ports to persistence-app. |
| `sovkora-assets` | SDK | Envelope encryption of generated assets. |
| `sovkora-persistence-app/src/main/resources/db/governance` | — | Interceptor policies (config plane), Flyway: `V1__governance_baseline.sql`, history table `flyway_history_governance` in the shared `orazaka_db`. |
| `infra/initdb/60-governance.sql` | — | Local bootstrap pointer only (nothing to create: role and database come from the workspace). |

A host applies the governance schema at startup by setting `orazaka.governance.migrations.enabled: true`: `GovernanceMigrations`
(`sovkora-persistence-app`) then runs after the host's own Flyway migration, with its migrator connection and settings, and its own
history table. The files sit in `db/governance`, outside `db/migration`, so the host's scan never sees them (two `V1` would collide).
Governance baselines at version `0`: on a shared database where another context already created its tables, the default
`baseline-on-migrate` at 1 would skip V1.

Hosts: [sovkora-conversation-service](https://github.com/krizaka/sovkora-conversation-service) (interactive) and
[sovkora-job-service](https://github.com/krizaka/sovkora-job-service) (async).

## Use it

```xml
<dependency>
    <groupId>com.krizaka.sovkora</groupId>
    <artifactId>sovkora-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Module boundaries (ADR-092 §4, #112)

The engine's domain is pure Java 21. Spring AI is confined to `sovkora-adapter-spring-ai`, which
implements the ports: inference (`ChatGeneratorClient`), embedding (`EmbeddingClient`), structured
output (`StructuredOutputClient`) and tools (`ToolRegistry`, results cached through `ToolResultCache`).
`sovkora-architecture` counts the classes that still break the rules and records them in
`architecture-ratchet.json`: a new violation fails `mvn verify`, a fixed one never does. Fixed some?
`./mvnw -pl sovkora-architecture -am test -Darchitecture.ratchet.update=true` lowers the baseline
(it never raises it). Every PR of the migration lowers at least one count.

### Structured output: validated against its schema, self-corrected, bounded (ADR-094 §4.6, #172)

`StructuredOutputClient.generate` asks for an instance of a type. The adapter puts Spring AI's `ChatClient` and its `StructuredOutputValidationAdvisor` in front of the model: each answer is validated against the type's JSON schema and, when it does not match (or is cut off), the model is asked again with the violation named. The number of answers one call may ask for is the `StructuredOutputBounds` port, read for the request's feature key from `orazaka_feature_structured_bounds` (1 to 5, the row `*` is the default, seeded at 2: the first plus one correction), edited by an administrator in the console (`/engine/tools`) and read on every call. A model whose options take a JSON schema (`StructuredOutputChatOptions`: OpenAI, Gemini, Anthropic) is asked in its native mode; any other falls back to the format in the prompt, and the advisor checks the answer either way. Out of attempts, the call fails with a typed `StructuredOutputInvalidException` (an `IllegalStateException`, with the type and the attempts): never a default value, so a caller can turn it into a control verdict. Every attempt is a model call, so it is observed and counted in the same way as any other (`gen_ai.*`).

### Tool loop: server tools run inside the engine (ADR-094 §4.1, #169)

Spring AI 2 no longer loops on tool calls inside the model, so `SpringAiChatInvocation` does it with the library's `ToolCallingManager`: when the model asks for tools, the engine runs them (already resolved, authorised and bound to the actor by the tool resolution), sends the results back and calls the model again, until it answers. Streaming does the same round by round; the server tool calls of intermediate rounds are not forwarded to the caller.

- **Client tools are never run here** (ADR-093): if any call of a round names a `ClientToolCallback`, the turn ends and every call goes back to the client.
- **The loop is bounded.** After `DEFAULT_MAX_TOOL_ROUNDS` (8) rounds of tool calls the turn fails with an `IllegalStateException` instead of looping. The bound is a constructor argument today; reading it per feature key from the database, and a run event per tool call, are tracked on #169.
- Token usage of all rounds is summed on the returned `ChatCompletion`.

### Control loop: a failed answer is never served

`ClosedLoopValidationInterceptor` screens every answer before it leaves (`screenResponse`). Each pass
ends in a typed `ControlVerdict`: `Validated` serves the text; `DriftAdjustment` re-runs the step with
the diff of what failed injected; when the retries (`max-retries`) are spent, or nothing can adjust the
answer, `HumanEscalation` refuses the turn (`PipelineShortCircuitException`, reason `control_escalation`).
Tier B (`sandbox-enabled`) rejects an unclosed code fence or an invalid `json` block, and a wired
`CodeSandboxPort` crash-tests each block. Adjustments and escalations are persisted as
`(runId, driftReason, humanActorId, timestamp, actionTaken)` through the outbox
(`ControlVerdictRecorder`, routing key `evt.control.verdict`). Streamed answers are not screened yet
(sovkora-engine#114).

### Route a step by policy

`StepRouter` takes a `StepRoutingRequest(stepId, capability, policy)`, a mandatory
`StepModelRecorder`, and a synchronous model invocation. Omitting the policy defaults to
`SOVEREIGN_LOCAL_ONLY`; only catalog rows explicitly declaring `runs_on = 'local'` qualify.
`COST_OPTIMIZED` and `LATENCY_FIRST` use the order the admin declares, not a hardcoded cost or
latency formula.

The existing `orazaka_runtime_config` table stores one row per capability/policy:

| `config_key` | `config_value` (JSON array, primary first) |
|:---|:---|
| `routing.text.generate.SOVEREIGN_LOCAL_ONLY` | `["local-primary","local-secondary"]` |
| `routing.text.generate.COST_OPTIMIZED` | `["economical-model","local-secondary"]` |
| `routing.text.generate.LATENCY_FIRST` | `["fast-model","local-secondary"]` |

These names are examples: the admin supplies names from `orazaka_models`. An empty array
disables a route. Missing/malformed configuration fails closed; it never uses a global default
or another policy. Policy rows and catalog eligibility are read on each execution, so an admin
edit needs no restart. Only `status = 'available'` models are attempted.

```java
var step = new StepRoutingRequest(stepId, "text.generate"); // sovereign by default
var result = stepRouter.execute(step, stepModelRecorder, modelName -> invokeModel(modelName));
```

The invocation must use the supplied model name and report provider unavailability as
`StepModelUnavailableException`. The synchronous `RoutedChatModel` adapter translates connection
failures, timeouts, and HTTP 502/503/504 into this portable signal. Only that signal advances to the next declared candidate;
validation, safety, billing, and recording failures propagate without retrying another model.
The recorder writes the selected name before each attempt, including the secondary on fallback.
Never wrap a lazy stream in this synchronous API: fallback must happen before output is emitted.

**Host integration:** this SDK does not own Studio's step table. The host supplies
`StepModelRecorder` to persist the selected `model_name` on its step and invokes `StepRouter`
from its executor. Existing `AiClient` calls do not implicitly opt into step routing; wiring
Studio/job execution to this new port belongs to those repositories. Interactive chat routing
is unchanged.

Public routing contracts carry Javadoc for the workspace documentation generator. Regenerate
the reference inside the assembled workspace with `sovkora docs build` and `sovkora docs sync`;
the generator and generated documentation are not present in this standalone repository.

## External content and secrets

- **Text the engine did not write is data, never an instruction.** What an MCP server returns and what the knowledge
  sources hold reach the model in a delimited user-role block (`ExternalContent`), cleaned of scripts and invisible
  characters, with one fixed rule of our own saying a block cannot change the instructions. No `SystemMessage` is built
  from external content (sovkora-engine#57, ADR-087).
- **MCP server tokens are sealed** for their owner with the platform's master keyring (`SecretSealer`, ADR-054) before
  they reach `user_mcp_servers` / `platform_mcp_servers`; rows written in clear are sealed at startup, and without a
  keyring a token is refused, never stored in clear (#58).
- **The subject pepper of the external checks is sealed** the same way, in the setting `checks.subject-pepper` of
  `orazaka_runtime_config` (for its own platform scope), never an environment variable. `SealedCheckSubjectPepperAdapter`
  (`sovkora-persistence-bridge`) opens it at each check; absent, in clear, unopenable or shorter than 32 bytes, it answers
  empty and no check runs. It is never logged, thrown or printed (`SealedCheckSubjectPepperAdapterTest`), and only that
  adapter and the sealing tool name the setting (`[CHK-001]`). The operator writes it with
  `java -cp … com.krizaka.sovkora.assets.infrastructure.tool.CheckSubjectPepperTool seal <keyfile>`, which draws 32 random
  bytes, seals them and prints the one `INSERT … ON CONFLICT` to run: nobody sees the pepper (krizaka/sovkora-packs#138).
- **`searchKnowledge`** searches the organisation's knowledge base, not the web (it was called `searchWeb`, #59).
  Searching and reading the web is ADR-087's grounded research.

## Grounded research: reading a page safely (ADR-087 slice 1)

`PageFetcher` (`sovkora-tools`, `PageFetchService`) reads one page for grounded research through an
administrator-declared `research_source`, with every guard of ADR-087 §4, in order:

1. research on (`research.enabled`) and the source enabled;
2. https, or http where the source allows it;
3. the source's allow and deny patterns, paths compared in canonical form (`/%70rivate` is `/private`);
4. the cache (`research_document`): a document read through **this** source, younger than the source's **current**
   freshness, whose final URL the source's current policy still allows — a hit never crosses sources;
5. the source's rate, then, on every hop, the host's rate;
6. robots.txt, always honoured, cached at most 24 h per origin and product token (RFC 9309); its own request follows
   redirects across authorities, and a robots.txt that cannot be read — a refused address included — allows nothing.

`PinnedHttpClient` then resolves each hop **once** and refuses any non-public address (loopback,
private, link-local and the metadata endpoint, CGNAT, IPv6 ULA, mapped forms…). It connects to exactly the address it
checked, so DNS rebinding cannot reach the connection. It re-checks every redirect, sends no cookies, caps the size,
and holds one deadline for the whole call (`research.fetch.timeout-ms`), so a body that trickles in stops with it.
`HtmlTextExtractor` decodes the bytes as the server said (the HTTP charset, else a BOM or `<meta charset>`), walks the
visible text in reading order — one paragraph per block, nothing hidden by `hidden`, `aria-hidden` or inline CSS —
and keeps the main content. A document is stored under its source, the URL asked for and the content hash, with its
final URL. Every call writes one `research_fetch` audit line, with a path hash, never the query. The limits are
`research.fetch.*` rows read at call time.
**Citations and the verifier (slice 2).** A grounded answer is `{answer, claims[], sources[]}`: each claim carries
citations, and each citation has a URL, a verbatim quote, a content hash and a fetch time. `CitationVerificationService`
is deterministic, with no model involved. A claim is `VERIFIED` only when one of its citations names a document **this
answer read** (the grounding stage records them in the `PromptContext`; the store is asked through that document's own
source), its quote — at most `research.quote.max-chars` — appears in that document after `QuoteNormalizer` (NFC, case
folding, French typography: « » “ ” ’, U+202F/U+00A0, œ/æ, dashes, the ellipsis), **and** every number, date and
reference the claim states appears in the quote, the document's title or the heading of the quote's section — a genuine
quote under a wrong number stays `TO_VALIDATE/UNSUPPORTED`. Any other claim is `TO_VALIDATE` with its reason
(`NO_SOURCE`, `URL_UNRESOLVED`, `QUOTE_NOT_FOUND`, `UNSUPPORTED`), or dropped in `STRICT`. Every URL, `owner/repo` or
statute stated in the model's prose that no verified claim covers becomes a `TO_VALIDATE` line (`UNLISTED_REFERENCE`).
**The served `answer` is rendered from the claims** — verified claims as they are, `[TO_VALIDATE]` prefixes in `MARK`,
verified claims only in `STRICT`, empty with no claims — never the model's prose, which is only scanned.
Numbers and dates match as **whole tokens** (« 14 » is not in « 140 » or « 2024 »), references **by identity**
(`art.` and `article` are one word, « art. 1 » is not « art. 12 », a URL only as the complete cited URL, case-sensitive).
A verified claim keeps **only the citations that passed**, and coverage trusts only those; unlisted references are added
in `MARK` only.
`CitationVerifierInterceptor` applies it to a citation-shaped answer and leaves any other answer untouched. It **fails
closed** (a verification that throws stops the turn), and it checks its own switches, because `Engine` hands every
interceptor bean to the output screen: `research.enabled` and its own `pipeline_interceptor_config` row, both off as
seeded — off means no rewrite. Slice 5's `grounding` flag turns it on.

## The law corpus: importers, manual import and `lookupStatute` (ADR-089 §2, #400)

The corpus is the dated, hashed text of the law the engine derives its checks from. It lives in the
**job-service tables** (`law_instrument`, `law_article_version`, `law_import`, created by
[sovkora-job-service](https://github.com/krizaka/sovkora-job-service) `infra/initdb/30-jobs-config.sql`), and the
engine reads and writes them through Spring Data JPA on its own data source, like the research tables: no second
connection pool (ADR-083). Nothing here runs while the setting `law.corpus.enabled` is false (the seeded default).

| | |
|:---|:---|
| **Importers** | `QuebecOpenDataSource` (the Données Québec statutes zip, conditional on its ETag, SHA-256 of the zip recorded) and `JusticeLawsXmlSource` (Justice Canada's `/eng/XML/<code>.xml` and `/fra/XML/<code>.xml`). Both implement `LawSource`, both use `LawStatuteXmlParser`, both send an honest user agent (`SovkoraLawWatcher/0.1`). Légis Québec and CanLII are not read. |
| **Watcher** | `LawWatchService` runs weekly (`orazaka.law.watcher.cron`, Monday 06:00 UTC) when `orazaka.law.watcher.enabled=true`: it imports every instrument of `law.import.instruments`, and every difference with the corpus opens one pull request in `krizaka/sovkora-packs` (a review file with the diff, both hashes and the citation). The first import of an instrument and language stores silently; an unchanged article opens nothing; a removed article is flagged. |
| **Manual import** | `LawManualImportService.importFile(ManualLawFile)`: the same parser and comparison, `mode = MANUAL`. `imported_by`, the address the file came from and the **SHA-256 of the file** are required (the service refuses the upload, and the table refuses the row, without them). Only an instrument of `law.import.instruments` is stored. The HTTP endpoint (`POST /api/v1/admin/law/imports`) belongs to the host that authenticates the administrator; it calls this service. |
| **`lookupStatute`** | `LawStatuteLookupService.lookupStatute(jurisdiction, instrument, ref, language, asOf)` returns the version in force on `asOf` (`in_force_from <= asOf < in_force_to`; no day means today) as `{text, contentHash, inForceFrom, officialUrl, importId}`, or `LAW_VERSION_UNKNOWN` (also: the article is unknown, the oldest text we hold took effect after `asOf`, or it was not in force then), or `LAW_CORPUS_DISABLED`. A later text is never read in place of an earlier one. As a model tool (`LookupStatuteTool`, arguments `jurisdiction`, `instrument`, `reference`, `asOf`, `language`) it is registered in the one `ToolRegistry` only with `orazaka.law.lookup.enabled=true`, because a registered tool is attached to every non-streaming request. |
| **Integrity (#89)** | A version is served only when its text, normalised again by the corpus's own rule (`ContentHash`: NFC, whitespace collapsed; the historical note is never hashed), still gives the `content_hash` recorded with its row. `JpaLawCorpusStore` carries that recorded hash (`LawArticleVersion.recordedHash`), never a recomputed one, and `LawArticleVersion.intact()` compares the two. A mismatch is `LAW_INTEGRITY_ERROR` with no text in `lookupStatute`, and an unknown article in a retrieval (never handed to the model); it is logged with the article's key and both hashes, never the text. ArchUnit `LawCorpusReadRulesTest` holds it: only `LawStatuteLookupService` and `LawCorpusResearchService` read an article by day (`[LAW-001]`), and no store builds a version from its text alone (`[LAW-002]`). |
| **Read API use case (#89)** | `LawArticleReadService.read(LawArticleQuery, actorId)` is what the host's `GET /api/v1/law/articles` calls (the controller lives in `sovkora-conversation-service`; this library has none). `asOf` is **required** (an ISO day; missing or unreadable is refused, never read from a clock); `language` is `fr` or `en`; an optional `quote` is checked against the article text under the same normalisation (`QuoteCheck`: `VERIFIED` or `NOT_IN_ARTICLE`, no model). Metered as one `RESEARCH` `CALL` on provider `law-corpus` through the `ResearchMeter` port: an invalid question and a switched-off corpus are answered before anything is held; then the call is held, **settled only on `FOUND`** (whatever the quote check), released on `LAW_VERSION_UNKNOWN`, `LAW_INTEGRITY_ERROR` or a failed read. No `ResearchMeter` bean means `LAW_METERING_UNAVAILABLE`: a read is never served unmetered. A budget that cannot hold the call is `LAW_BUDGET_EXCEEDED`. |

- **Every import is a `law_import` row**: source, `AUTOMATED` or `MANUAL`, dataset address, ETag, SHA-256, the day the
  consolidation is current to, who, start and end, counts (`articles_new`, `articles_changed` (a removal counts here),
  `articles_unchanged`) and `status` (`RUNNING`, then `SUCCEEDED`, or `FAILED` when nothing could be read or a change
  was refused). A source that cannot be read leaves its `FAILED` row.
- **A changed text closes the old row (`in_force_to` = the new in-force day) and inserts the new one, in one
  transaction**, with a row lock on the open version: two imports cannot both replace it. A **reverted** text is a new
  dated row (the key is `(instrument_id, ref, language, in_force_from)`, krizaka/sovkora#401), never the old row
  reopened. A text that changes without a later in-force date is refused and opens no request.
- **A change is applied only once its review request is open** (the watcher), so a failed request is detected again by the
  next run. Without a request port (no watcher), changes are applied directly and the import row is the trace.
- **Settings are the `law.*` rows of `orazaka_runtime_config`**, read at each call: `law.corpus.enabled`,
  `law.import.instruments` (`QC:CCQ-1991,CA:E-15,...`; whole instruments, nothing else is stored) and
  `law.import.max-article-chars` (a longer article fails the whole import before anything is written).
  Static wiring only: `orazaka.law.watcher.enabled|cron|quebec.dataset-url|github.app-id|github.installation-id|github.private-key|github.owner|repository|base-branch|api-url`
  (the krizaka-agents App's key comes from the deployment's secrets, never from code; the App mints its hour-long
  installation tokens at run time, so a static token is never configured) and `orazaka.law.lookup.enabled`.
- **`official_url_template`**: an instrument's citation address, with the placeholders `{ref}`, `{lang}`, `{lang3}`,
  `{acts}` and `{section}` (Justice Canada's paths differ by language). The importer writes it when it creates the
  instrument; the administrator can edit it.

## Retrieving the law through grounded research, and its cache (ADR-089 §2 and §7, #81)

`LawRetrievalService` (`sovkora-tools`) gives a kind of document the articles it is judged by, as verified claims. The kind's data
names the articles (`targets`, from the pack's `kinds/*.json`) and the question; the engine names none.

1. **Off unless three switches are on**: `law.corpus.enabled`, `research.enabled` and the `law-corpus` research source (all seeded
   off). The wiring is off too: `orazaka.law.retrieval.enabled=true` registers `LawRetrievalConfiguration`. A disabled call reads
   nothing, asks no model and costs nothing (`DISABLED`).
2. **The `law-corpus` provider reads the corpus, never the network** (`LawCorpusResearchService`). For the instruments and references
   the kind targets it reads `law_article_version` at `asOf` and offers each article as a `research_document`
   (`{text, contentHash, inForceFrom, officialUrl, importId}`; URL = the official page, hash = the article's, title = « CCQ-1991, art. 1931 »),
   so the ADR-087 `CitationVerifier` checks every quote **unchanged**. An article with no version in force that day is
   `LAW_VERSION_UNKNOWN`, listed in `unknown`, never replaced by a later text; no article at all means `NO_ARTICLES` and no model call.
   `law.retrieval.max-articles` bounds what one retrieval keeps. **`asOf` is required** (a request without it is refused): the engine never reads "today" from a clock's own zone; the caller passes the document's date, or the calendar day in the jurisdiction's zone (`America/Toronto` for QC).
3. **The cache is `law_retrieval`**, keyed by jurisdiction, kind, **language** (claims written in French are never served for English) and the **as-of bucket** (the newest `in_force_from` among the articles).
   A row is served (`CACHED`: no model call, no charge) only when it is not `stale`, is younger than `law.retrieval.max-age-days`
   and cites exactly the article versions (`instrument`, `ref`, `language`, `contentHash`) the corpus holds for that day.
4. **A changed cited article marks the cache stale**: `JpaLawCorpusStore.replace` and `.close` set `stale` on every `law_retrieval` that
   cites the article (JSONB containment), in the same transaction as the change, and the next call recomputes. Saving a recomputed
   retrieval is one `INSERT … ON CONFLICT DO UPDATE`: two calls computing the same key leave one row and fail neither.
5. **Each computed retrieval is one metered grounded call**, a `RESEARCH` operation of unit `CALL`, priced by the pricebook row of the
   provider key (`ResearchMeter.hold`, then `settle`; `release` if the model fails, never masking the original error), and counted
   against the run budget (ADR-088). A budget that cannot hold the call refuses it before the model is asked (`BUDGET_EXHAUSTED`,
   nothing cached). A cache hit is free.
6. **The model is a port** (`GroundedClaimsModel`): the only place a retrieval calls one. Verified claims are cached with their
   citations; `TO_VALIDATE` ones are kept as such and are not used by the derivation stage.

The model and the meter come from the grounded-research capability (ADR-087 slice 5, `orazaka.core.research.grounded`) and the run
budget (ADR-088). Until they are wired the service **fails closed**: no meter means the call is refused (`BUDGET_EXHAUSTED`), no
model means an error, never an unmetered call. **The meter is `CreditHoldResearchMeter`** (`sovkora-tools`, registered by
`CallMeteringAutoConfiguration` wherever a billing `CreditAuthorizationClient` exists): a `RESEARCH` hold priced by the
pricebook row `(RESEARCH, <provider key>, CALL)`, settled with `calls: 1` under the key `research:<holdId>`, released on a
failure; insufficient credits are `BUDGET_EXHAUSTED`. The same configuration registers `CreditHoldCheckMeter` for the
external checks (`EXTERNAL_CHECK`, `model_name` = the connector's provider, key `external-check:<holdId>`). A host without a
billing client gets neither, and the fallbacks above apply. Only these adapters speak the billing contract in `sovkora-tools`
(`[BILL-001]`), and they never log (`[BILL-002]`). Tests: `LawRetrievalServiceTest` (the golden case: a visit notice brings art. 1931 to
1933 and no rent article; a cached retrieval makes no model call, asserted on the model port; a changed article recomputes; off by
default; metering and the budget), `JpaLawRetrievalCacheIT` (JSONB, the stale mark in the same transaction, concurrent saves),
`LawRetrievalConfigurationTest`.

## Deriving proposed rules from the law: `vet` and `rule_proposal` (ADR-089 §3, #82)

`RuleDerivationService.derive(LawRetrievalRequest)` (`sovkora-tools`) proposes the checks of a kind of document from its law.
Nothing it proposes judges a document. A proposal becomes a ruleset only through promotion (stage 5, a pull request to sovkora-packs).

1. **`law.derive.max-credits` is 0** (or its row is missing): nothing runs. The result is `DERIVATION_OFF`.
2. **Retrieval.** The kind's law is retrieved through `LawRetrievalService`, cache first. Only the **`VERIFIED`** claims are used.
   An article with none gets no call.
3. **Model calls.** The model port `RuleDerivationModel` is called once per article, in the retrieval's order. Before each call,
   the credits spent plus the port's `callCeiling()` must fit in `law.derive.max-credits`. When they do not, the run stops
   (`BUDGET_EXHAUSTED`): what was already derived is vetted and recorded, and `notDerived` lists the rest. No call is made
   once `law.derive.max-rules-per-kind` rules are proposed.
4. **`RuleProposalVet` (domain, no model).** It rejects a rule for each of these reasons (`VetReason`):
   - `SCHEMA_INVALID`: not a ruleset rule of `REQUIRED_PATTERN`, `FORBIDDEN_PATTERN` or `JUDGMENT`, or a `JUDGMENT` that is not `ADVISORY`;
   - `REGEX_INVALID`: the pattern does not compile, uses a construct the Python worker reads differently, or nests unbounded quantifiers;
   - `REGEX_MATCHES_EMPTY`: the pattern matches the empty string;
   - `LAW_VERSION_UNKNOWN`: no version is in force on `asOf`, or the version cited is another one;
   - `QUOTE_NOT_IN_ARTICLE`: the quote is not in the article;
   - `UNSUPPORTED_NUMBER`: a number the rule states (« 24 heures », « trois mois ») is in none of its quotes;
   - `SUBJECT_MISSING`: a `FORBIDDEN_PATTERN` or `JUDGMENT` rule has no `subject`;
   - `FIXTURES_INDISTINCT`: the rule does not tell its own fixture pair apart (ADR-072);
   - `OVER_RULE_LIMIT`: the rule comes past `law.derive.max-rules-per-kind`.
5. **`RuleProposalService`, the only door to `rule_proposal` (job-service).** It writes `PROPOSED`, or `REJECTED_AUTO` with
   the `vet_report`. Under a transaction lock per (jurisdiction, kind), it computes the next `proposal_revision`; the
   unreviewed `PROPOSED` revision of the same rule becomes `SUPERSEDED`. It also gives the reads of the review queue
   (`find`, `list(status, kindId)`).

**A proposal cannot skip vet.** The store (`RuleProposalStore`, `JpaRuleProposalStore`) takes only a `VettedProposal`.
Only `RuleProposalVet` constructs one (`[LAW-003]`), and only `RuleProposalService` calls the store (`[LAW-004]`). Both are
ArchUnit rules in `RuleProposalRulesTest`, each also run on a planted violation.

**Wiring.** `orazaka.law.derivation.enabled=true` wires it, on top of the retrieval. Without a host `RuleDerivationModel`,
a call is refused.

**The cap and ADR-088.** The credit check is the run budget of ADR-088 (proposed) applied to the one paid step. Once it
lands, a derivation becomes a `rule-derivation` Studio run with `credit_cap = law.derive.max-credits`, and the saga makes
the same comparison before each step.

**Tests.** The data is mirrored from sovkora-packs `document-validation/derivation/`:
- `RuleProposalVetTest`: each planted proposal is rejected for exactly its reason, and the golden visit-notice rules pass;
- `RuleDerivationServiceTest`: art. 1931 to 1933 parsed from the official XML, recorded model answers, the cap, the verified-only rule, an article outside the corpus;
- `JpaRuleProposalStoreIT` (Testcontainers): JSONB, revisions, and eight concurrent derivations giving distinct revisions.

## The review queue and promotion (ADR-089 §5 and §8, cut 9)

The use cases behind the console's **Law → Proposals** screen. The host (sovkora-conversation-service,
`/api/v1/admin/law/**`) authenticates the administrator and audits each write; every rule is enforced here.

| Use case | What it does |
|:---|:---|
| `RuleProposalService.approve(id, reviewer, note)` | `PROPOSED` → `APPROVED`, with who and when. Any other status is refused (`PROPOSAL_NOT_REVIEWABLE`). |
| `RuleProposalService.reject(id, reviewer, reason)` | `PROPOSED` or `APPROVED` (in no pull request yet) → `REJECTED`. The reason is required. |
| `RuleProposalReviewService.edit(id, rule)` | Writes a new `proposal_revision`, vetted again by `RuleProposalVet` on the day the proposal was vetted. If it passes, it is `PROPOSED` and the `PROPOSED` / `APPROVED` revisions of the rule become `SUPERSEDED`: an edit after approval returns to review. If the vet refuses it, it is `REJECTED_AUTO` and the previous revision is left as it was. |
| `RulesetPromotionService.promote(kindId, ids, effectiveFrom, note)` | Opens the packs pull request; see below. |
| `RulesetPromotionService.goldenCases(kindId)` | The kind's golden cases (`worker/fixtures/<kindId>/*.txt` in sovkora-packs) against `law.review.min-golden-cases`. |
| `KindDerivationService.start(jurisdiction, kindId, asOf, language, actor)` | Starts a derivation. The kind's targets and question are read from the pack's `kinds/<kindId>.json`, never typed by the caller. |
| `LawCorpusCatalogueService` | The Corpus tab: instruments with their counts, imports, and an article by point in time. It returns metadata only. The text leaves only through the metered read. |
| `KindDemandService` | The Demand tab: `kind_demand`, the most asked first. |

**Promotion refuses (409)** before anything is opened, and checks the statuses again while the rows are locked:
- `PROPOSAL_NOT_APPROVED`: a proposal is not `APPROVED`;
- `ALREADY_IN_REVIEW`: a proposal is already in a pull request;
- `KIND_MISMATCH`, `DUPLICATE_RULE`;
- `GOLDEN_MINIMUM_NOT_MET`: fewer than `law.review.min-golden-cases` golden cases, or no `compliant`, `defective` or `blank` one among them;
- `LAW_VERSION_MISMATCH`: a pinned hash is not the version in force on `effectiveFrom`;
- `QUOTE_TOO_LONG`: a quote is over 300 characters.

A new revision of an existing version must say what it corrects.

**The pull request.** `GitHubRulesetPromotionPublisher` makes one commit on a new branch, `law-promote/<kindId>/<effectiveFrom>-r<revision>-<id>`. The commit holds:
- `rulesets/<kindId>/<effectiveFrom>.json`: `version` is the in-force day, then `revision` / `revisionNote`, `reviewStatus: REVIEWED_INTERNAL` (the `TO_REVIEW_BY_LAWYER` mark stays in `reviewNote`), the `law` blocks pinned with `inForceFrom` and `officialUrl`, and `provenance` (`derivationRunIds`, `proposalIds`, `reviewedBy`, `reviewedAt`, `lawImportIds`);
- the pinned article versions under `law/`;
- `kinds/<kindId>.json` set to `PROMOTED`.

It then opens the pull request against `main`. It never writes `main`. The merge, by a person, is the promotion. Meanwhile the proposals stay `APPROVED` and carry `packs_pr_url`.

**The GitHub App, never a personal token.** `GitHubAppInstallationTokens` signs the App's JWT (RS256, 9 minutes) and mints
an installation token, renewed 5 minutes before it ends. It reads the private key as GitHub gives it (PKCS#1) or as PKCS#8.
Properties (`RulesetPromotionProperties`, off by default):

| Property | Default |
|:---|:---|
| `orazaka.law.promotion.enabled` | `false`. When off, promotion and golden reads answer `LAW_PROMOTION_OFF` (503). |
| `orazaka.law.promotion.app-id`, `installation-id`, `private-key` | required when on (the `krizaka-agents` App: contents and pull requests on sovkora-packs only) |
| `orazaka.law.promotion.owner` / `repository` / `base-branch` / `api-url` | `krizaka` / `sovkora-packs` / `main` / `https://api.github.com` |

**Rules.**
- `[LAW-005]`: only `RulesetPromotionService` opens a promotion (`RulesetPromotionPublisher.open`, `RuleProposalService.promote`).
- `[LAW-006]`: only `RuleProposalReviewService` records an edit (`RuleProposalService.revise`), so no edit skips the vet.

Both are in `RuleProposalRulesTest`, each run on a planted violation (`fixture/PromotionThatSkipsTheGate`).

**Tests.**
- `RuleProposalReviewTest`, `RulesetPromotionServiceTest`: every refusal, and that a refused promotion opens nothing.
- `GitHubRulesetPromotionPublisherTest`, `GitHubRulesetPackTest`, `GitHubAppInstallationTokensTest`: the GitHub API is recorded (`src/test/resources/github/promotion/`), and no test calls GitHub.
- `JpaRuleProposalStoreIT`: conditional decisions, an edit superseding an approval, a failed promotion writing nothing, and four concurrent promotions opening one pull request.
- `LawReviewReadsIT`: the Corpus and Demand reads.

## Observability: every model call is a `gen_ai` observation (ADR-094 slice 3, #171)

Every model the engine builds carries the host's `ObservationRegistry` (a no-op one without a host): chat models and the tool
rounds through Spring AI, embeddings and text to speech by the engine with the same keys. `gen_ai.client.operation` and
`gen_ai.client.token.usage` are tagged with the provider, the model and `orazaka.feature` (the feature key of the turn,
`none` outside a turn) and **never** carry a prompt, an answer, an input or a tool argument (ADR-064; pinned by
`ModelObservationsTest`, `EmbeddingAndSpeechObservationsTest`, `FeatureTagObservationsTest`). Money stays on our metering;
[docs/cost-reconciliation.md](docs/cost-reconciliation.md) has the query that sets the tokens observed against the usage billed.

## Position in the platform

| | |
|:---|:---|
| Depends on | [`sovkora-build`](https://github.com/krizaka/sovkora-build) · [`sovkora-contracts`](https://github.com/krizaka/sovkora-contracts) · [`krizaka-billing`](https://github.com/krizaka/krizaka-build/tree/main/krizaka-billing) · [`sovkora-studio-service`](https://github.com/krizaka/sovkora-studio-service) |
| Used by | [`sovkora-conversation-service`](https://github.com/krizaka/sovkora-conversation-service) · [`sovkora-job-service`](https://github.com/krizaka/sovkora-job-service) |
| Workspace path | `orazaka-libs/orazaka-ai-engine` |

## Build

**Inside the Sovkora workspace** (recommended — every dependency is built from source):

```bash
git clone https://github.com/krizaka/sovkora.git && cd sovkora
node scripts/workspace.mjs clone          # clones every repository at its workspace path
./mvnw -f orazaka-libs/orazaka-ai-engine/pom.xml verify
```

**Standalone** — upstream artifacts must be in `~/.m2` (built by the workspace) or resolvable from
GitHub Packages (`https://maven.pkg.github.com/krizaka/<repository>`, see the
[workspace README](https://github.com/krizaka/sovkora#consuming-packages)):

```bash
./mvnw verify
```

Requirements: JDK 21, Docker (Testcontainers integration tests).

## Governance

This repository follows the Sovkora governance contract — [AGENTS.md](https://github.com/krizaka/sovkora/blob/main/AGENTS.md)
in the workspace is normative; the local [AGENTS.md](AGENTS.md) only scopes it to this repository.

<!-- krizaka:workflows:start -->
## Workflows

What runs in the Actions tab of this repository.

- **CI** (`ci.yml`): Lint, tests and build of the repository. Runs on push, on every pull request, on demand.
- **Dependabot auto-merge** (`dependabot-automerge.yml`): Auto-merges safe Dependabot updates. Runs on every pull request.
- **Merge resolution** (`merge-resolution.yml`): Fails a PR that drops what main added without a deletion-explained line. Runs on every pull request.
- **Release notes** (`release-notes.yml`): Writes the release notes. Runs on push.
- **Scorecard** (`scorecard.yml`): OpenSSF Scorecard of the repository. Runs when branch rules change, on a schedule, on demand.
- **Security scan** (`security.yml`): Secret scan (gitleaks) and workflow scan (zizmor); both are required checks. Runs on every pull request, in the merge queue, on push.
<!-- krizaka:workflows:end -->

## License

[FSL-1.1-ALv2](LICENSE.md) (Functional Source License, Apache-2.0 future licence) © Technologies Krizaka Inc. — read,
run, modify and self-host it for any purpose except offering a competing service; each version becomes Apache-2.0 two
years after its release. Commits before the licence change remain [Apache-2.0](LICENSE-APACHE) — see [NOTICE](NOTICE)
and [ADR-012](https://github.com/krizaka/.github/blob/main/docs/adr/ADR-012-licensing-libraries-open-products-fair-source.md).
The Krizaka names and birds are not licensed: [trademark policy](https://github.com/krizaka/.github/blob/main/TRADEMARKS.md).
