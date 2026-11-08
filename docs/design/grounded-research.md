---
title: "Design — Grounded research"
description: "How ADR-087 is built: the request path, the tables and settings, the engine and admin APIs, what a product sends, the console screens and the slices with their acceptance criteria."
category: Design
---

# Design — Grounded research (ADR-087)

The decision and its reasons are in ADR-087. This page says how it is built.
Anything marked **TO VALIDATE** stays disabled until it is checked.

## Architecture

A grounded answer goes through the existing pipeline. The new parts are in **bold**.

| # | Stage | Where | What happens |
|:-:|:---|:---|:---|
| 1 | Request | Product BFF → edge → router (chat) or Studio → job plane (`CAPABILITY` step) | The request says `grounding.enabled = true`, or the step's feature key is `orazaka.core.research.grounded`. |
| 2 | Entitlement, safety, scope | existing interceptors | Unchanged. |
| 3 | **Plan** | **`GroundingInterceptor`** (enrichment, `orazaka-interceptors`) | One short model call turns the question into ≤ `research.max-searches` queries and candidate official references. No answer yet. |
| 4 | **Resolve sources** | **`ResearchSourceResolver`** (`orazaka-tools`) | Reads the `research_source` rows in scope (Studio > pack > actor > global), keeps the enabled ones, and applies the data-class rule (Loi 25). |
| 5 | **Search** | **`SearchProvider`** port → `SearxngSearchProvider` / `BraveSearchProvider` | Results (title, URL, snippet) are filtered by the source's allow and deny patterns. |
| 6 | **Look up** | **`OfficialSource`** port → `JusticeLawsSource`, `CanliiSource`; **`CodeHost`** → `GithubCodeHost` | Direct lookups when the question names a statute, a decision or a repository. |
| 7 | **Fetch & extract** | **`PageFetcher`** with **`SafeHttpClient`** | SSRF guard, robots, rate, size and time caps; extraction to text; the document is stored once in `research_document` (URL + hash), and every call writes `research_fetch`. |
| 8 | **Ground the prompt** | `GroundingInterceptor` | The extracts go into the prompt as quoted, delimited **data** blocks (user role), each with its URL and hash. The system prompt requires a citation for every claim. |
| 9 | Model | existing routing (`DynamicModelRouter`, per-step routing #54) | Produces the answer in the citation shape. |
| 10 | **Verify** (buffered: nothing is sent before it) | **`CitationVerifierInterceptor`** (validation) | Quote found in the stored document, after normalisation (NFC, case folding, French typography: « » “ ” ’, U+202F/U+00A0, œ/æ)? URL resolved? Unlisted URLs, repositories and statutes checked? Each failure downgrades the claim to `TO_VALIDATE` (mode `MARK`) or drops it (mode `STRICT`). |
| 11 | Contract | Studio `OutputContract` (#59) | The step's output must match the citation schema below, or the step fails as `EXECUTOR_FAULT`. |
| 12 | Metering | `ChatSettlementListener` / job metering → billing | Tokens as `CHAT`; each search, lookup and fetch that is not a cache hit as `RESEARCH`/`CALL`. |

Stages 4 to 7 are also tools in `DefaultToolRegistry` (`search`, `fetchPage`, `lookupStatute`, `lookupRepository`), so an
agent loop (ADR-086) can call them directly, with the same guards.

## Data model

All tables sit with the engine's other tables (`orazaka-job-service` `infra/initdb/30-jobs-config.sql`), with a matching
dev-deploy migration in the workspace.

### `research_source`

| Column | Type | Notes |
|:---|:---|:---|
| `id` | UUID PK | |
| `source_key` | VARCHAR(80) UNIQUE | e.g. `web-search`, `justice-laws`, `canlii`, `github-krizaka` |
| `kind` | VARCHAR(20) | `SEARCH`, `FETCH`, `OFFICIAL`, `CODE_HOST`, `MCP` (CHECK) |
| `provider` | VARCHAR(40) | `searxng`, `brave`, `http`, `justice-laws`, `canlii`, `legisquebec`, `registraire`, `github`, `mcp` — a value, never a type |
| `scope` | VARCHAR(10) | `GLOBAL`, `PACK`, `STUDIO`, `ACTOR` (CHECK) |
| `scope_ref` | VARCHAR(255) NULL | pack key, Studio key or actor id; NULL for `GLOBAL` |
| `base_url` | VARCHAR(1000) NULL | the provider's endpoint (SearXNG instance, API base) |
| `allow_patterns` | TEXT[] | host and path globs, e.g. `laws-lois.justice.gc.ca/eng/*`; empty means none (closed by default) |
| `deny_patterns` | TEXT[] | checked after allow |
| `freshness_seconds` | INT | cache lifetime of what this source returns |
| `rate_per_minute` | INT | token bucket per source; per-host buckets come from `research.fetch.per-host-per-minute` |
| `allows_personal_data` | BOOLEAN DEFAULT FALSE | Loi 25: only such sources, with consent, receive a query from a `SENSITIVE`/`REGULATED` context |
| `credential_ref` | VARCHAR(255) NULL | id of the encrypted credential (ADR-054), never the secret itself |
| `enabled` | BOOLEAN DEFAULT FALSE | LégisQuébec and Registraire are seeded disabled (TO VALIDATE) |
| `verify_mode` | VARCHAR(10) NULL | `MARK` or `STRICT`; NULL means `research.verify.mode` |
| `created_by`, `created_at`, `updated_at` | | the change is also in `operator_audit` (ADR-085) |

### `research_document` (the cache)

| Column | Type | Notes |
|:---|:---|:---|
| `source_id` | UUID FK, NOT NULL | the source that authorised every hop; part of the key (a hit serves only that source) |
| `url` | TEXT | the URL as requested, normalised |
| `final_url` | TEXT | the URL after redirects (what a citation names) |
| `content_hash` | CHAR(64) | SHA-256 of the extracted text |
| PK | `(source_id, url, content_hash)` | the same URL with new content is a new row; a citation names the hash it read; a hit is served after the source's current policy and freshness |
| `source_id` | UUID FK | |
| `fetched_at` | TIMESTAMPTZ | |
| `http_status` | SMALLINT | |
| `content_type`, `bytes` | | |
| `etag`, `last_modified` | TEXT NULL | conditional refetch |
| `title` | TEXT | |
| `text` | TEXT | extracted text, capped by `research.fetch.max-bytes` |
| `expires_at` | TIMESTAMPTZ | `fetched_at + freshness`; a purge job applies retention |

### `research_fetch` (the audit)

`id`, `at`, `actor_id`, `run_id` or `chat_id`, `source_id`, `operation` (`SEARCH`, `FETCH`, `LOOKUP`), `host`, `path_hash`
(never the query or the path in clear), `http_status`, `bytes`, `duration_ms`, `cache_hit`, `refused_reason` (`SSRF`,
`ROBOTS`, `ALLOWLIST`, `RATE`, `SIZE`, `TIMEOUT`, `CONSENT`). Kept as long as the run's data class allows (ADR-065).

### `research_consent`

`actor_id`, `source_id`, `purpose` (text shown at consent), `granted_at`, `revoked_at`. Read only for sources with
`allows_personal_data`.

### Settings (`orazaka_runtime_config`)

| Key | Default | Meaning |
|:---|:---|:---|
| `research.enabled` | `false` | master switch; production stays off until SearXNG is validated |
| `research.max-searches` | `3` | per answer, declared into the run budget of ADR-088 |
| `research.max-fetches` | `8` | per answer |
| `research.max-calls` | `16` | every billable research operation of one answer (searches, fetches, lookups, the verifier's checks), declared into the run budget of ADR-088 |
| `research.fetch.timeout-ms` | `10000` | connect + read |
| `research.fetch.max-bytes` | `2097152` | 2 MB, before extraction |
| `research.fetch.max-redirects` | `3` | each hop re-checked |
| `research.fetch.per-host-per-minute` | `30` | token bucket per host |
| `research.fetch.content-types` | `text/html,application/xhtml+xml,text/plain,application/xml,application/pdf` | |
| `research.quote.max-chars` | `300` | longest quoted passage |
| `research.verify.mode` | `MARK` | `MARK` keeps unverified claims as `TO_VALIDATE`; `STRICT` drops them |
| `research.cache.default-freshness-seconds` | `86400` | when a source sets none |
| `research.user-agent` | `OrazakaResearch/{version} (+https://www.sovkora.com)` | honest, never a browser string |

These defaults are first values, not measurements. Slice 5 measures latency and cost before any default changes.
robots.txt is always honoured, so it has no setting.

### Pricebook (`credit_pricebook`, krizaka-billing)

New `capability = RESEARCH`, `unit = CALL`. One row per `model_name` (provider): `searxng`, `brave`, `fetch`,
`justice-laws`, `canlii`, `github`. `orazaka_capabilities.billable_capability` gains `RESEARCH` in its CHECK. A cache
hit is not billed.

## The citation shape (output contract)

Written within the keywords `OutputContract` enforces (`type`, `required`, `properties`, `items`, `minItems`,
`minLength`, `maxLength`, `enum`):

```json
{
  "type": "object",
  "required": ["answer", "claims", "sources"],
  "properties": {
    "answer": { "type": "string", "minLength": 1 },
    "claims": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["text", "status", "citations"],
        "properties": {
          "text": { "type": "string", "minLength": 1 },
          "status": { "type": "string", "enum": ["VERIFIED", "TO_VALIDATE"] },
          "reason": { "type": "string" },
          "citations": {
            "type": "array",
            "items": {
              "type": "object",
              "required": ["url", "quote", "contentHash", "fetchedAt"],
              "properties": {
                "url": { "type": "string", "minLength": 8 },
                "quote": { "type": "string", "minLength": 1, "maxLength": 300 },
                "contentHash": { "type": "string", "minLength": 64, "maxLength": 64 },
                "fetchedAt": { "type": "string" }
              }
            }
          }
        }
      }
    },
    "sources": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["url", "title", "sourceKey"],
        "properties": {
          "url": { "type": "string" },
          "title": { "type": "string" },
          "sourceKey": { "type": "string" }
        }
      }
    }
  }
}
```

A `VERIFIED` claim has at least one citation whose quote the verifier found in a document **this answer read**, and
every number, date and named reference of the claim is in that quote or in the document's title or section heading.
The `answer` field is **rendered by the verifier** from the claims after verification (`[TO_VALIDATE]` prefixes in
`MARK`, verified claims only in `STRICT`, empty with no claims); the model's own prose is never served. A `TO_VALIDATE` claim carries a `reason`
(`NO_SOURCE`, `QUOTE_NOT_FOUND`, `URL_UNRESOLVED`, `UNSUPPORTED`, `SOURCE_DISABLED`, `UNLISTED_REFERENCE`, `BUDGET`). The rule « a `VERIFIED`
claim has a citation » is enforced by the verifier, because the contract subset has no conditional keyword.

## APIs

### Engine

| Call | Who | Body / answer |
|:---|:---|:---|
| Chat or text request | products, web client | existing body + `"grounding": { "enabled": true, "sources": ["justice-laws"]?, "mode": "MARK" \| "STRICT"? }`; the answer is in the citation shape |
| Studio step | blueprints | `{ "kind": "CAPABILITY", "featureKey": "orazaka.core.research.grounded", "inputs": { "question": "…", "sources": [...]?, "jurisdiction": "QC"? }, "outputContract": <citation schema> }` |
| Tools | agent loops (ADR-086) | `search {query, sourceKey?}`, `fetchPage {url}`, `lookupStatute {jurisdiction, reference}`, `lookupRepository {owner, name}`, all `READ_ONLY` |

### Admin (router, `ADMIN` only, every write in `operator_audit`)

| Method and path | Purpose |
|:---|:---|
| `GET /api/v1/admin/research/sources` | list, with scope, state and last-fetch health |
| `POST /api/v1/admin/research/sources` · `PUT …/{id}` · `DELETE …/{id}` | manage a source; a credential is set through `PUT …/{id}/credential` and never read back |
| `POST /api/v1/admin/research/sources/{id}/test` | one safe fetch or search, showing what the guards decided |
| `GET /api/v1/admin/research/fetches?source=&refused=&from=` | the audit, without queries or paths in clear |
| Settings | the existing runtime-settings screen (orazaka-web-client#264), `research.*` keys |

The edge already sends `/api/v1/admin/**` (other than users) to the router's catch-all, so these paths need no new edge
route. Slice 6 checks that with a test.

## What a product does

Nothing beyond one of these two:

- **chat or text**: add `"grounding": { "enabled": true }` to the request it already sends;
- **Studio**: use a Studio whose step is `orazaka.core.research.grounded`, or add that step to a pack's blueprint.

The product renders `claims` with their citations, and shows `TO_VALIDATE` as such, never as a fact. A Krizaka UI
component for that is a follow-up in krizaka-ui (not in this ADR's slices).

## Console (admin.sovkora.com, `orazaka-web-client` `src/app/console`)

- **Sources**: a table (key, kind, provider, scope, enabled, freshness, rate, last status), an edit form with allow and
  deny patterns, the « test » button, and the credential set-only field. LégisQuébec and Registraire are shown disabled
  with « TO VALIDATE: access conditions ».
- **Audit**: fetches and searches by source, with refused reasons, filters and no content in clear.
- **Settings**: the `research.*` keys on the Engine screen (#264).
- **Costs**: the `RESEARCH` pricebook rows on the existing billing screen.

## Rollout

Each slice is one pull request per repository. Each is test-first, with its README and generated docs.

| Slice | Repository | Scope | Accepted when |
|:-:|:---|:---|:---|
| 0 | orazaka-ai-engine | Rename `searchWeb` to `searchKnowledge` (description: the corpus); `McpInterceptor` puts MCP content as a quoted user-role data block, not a `SystemMessage`; MCP `auth_token` encrypted at rest | Tests prove: no `SystemMessage` from external content; the tool's name and description say « knowledge »; a stored token is not readable in the table |
| 1 | orazaka-ai-engine, orazaka-job-service | `SafeHttpClient`, `PageFetcher`, extraction, `research_document`, `research_fetch`, robots (24 h cache), settings rows, dev migration | Refused: loopback, RFC 1918, link-local, `169.254.169.254`, IPv6 ULA and mapped forms, rebinding (a resolver that answers public then private), redirect to private, > max bytes, > timeout, disallowed by robots; each with an audit row and a reason. A cached refetch inside freshness makes no HTTP call. A hit is served only through the same source, under its current policy and freshness; a redirected document keeps its final URL; percent-encoded paths are compared in canonical form; a slow-trickle body stops at the deadline |
| 2 | orazaka-ai-engine, orazaka-studio | `CitationVerifierInterceptor`, the citation contract, modes | On recorded documents: a quote present → `VERIFIED`; altered by one word → `TO_VALIDATE/QUOTE_NOT_FOUND`; an unlisted URL or `owner/repo` in the answer → checked or `TO_VALIDATE`; `STRICT` drops instead; contract breach fails the step. A genuine quote attached to a wrong number or statute stays `TO_VALIDATE/UNSUPPORTED`; the served `answer` is rendered from the verified claims (a rejected claim repeated in the model's prose never reaches the client; no claims → empty answer); a document not read by this answer never verifies |
| 3 | orazaka-ai-engine, workspace infra | `SearchProvider`, `SearxngSearchProvider` (JSON format enabled), `BraveSearchProvider`, search cache, Loi 25 rule, SearXNG service in compose and dev deploy | A `SENSITIVE` query to a source without `allows_personal_data` + consent is refused with `CONSENT`; results outside the allowlist are dropped; Brave results are not stored unless the source says storage is allowed. A `STANDARD` chat question naming a person is not searched without consent; the search cache is keyed by source, not provider |
| 4 | orazaka-ai-engine | `JusticeLawsSource` (XML index), `CanliiSource` (key requested), `GithubCodeHost` (App token, read-only); LégisQuébec and Registraire adapters present but disabled | Lookups by reference return the official URL and text; a repository that does not exist is reported as such; disabled sources answer `SOURCE_DISABLED` |
| 5 | orazaka-job-service, krizaka-billing, orazaka-ai-engine | Capability `orazaka.core.research.grounded`, `grounding` flag, `RESEARCH` pricebook, holds sized on the caps | A grounded run is billed tokens + non-cached calls exactly once; latency and cost measured on 20 questions and written in the PR. A grounded request is never streamed token by token and never answered or stored by the semantic cache; fifty repository references stop at `research.max-calls` and the bill never exceeds the hold |
| 6 | orazaka-web-client | Console: sources, audit, settings keys | Component and API-mocked tests; dark and light; FR/EN; only `@krizaka/ui` |
| 7 | orazaka-packs | Golden cases (legal brief of krizaka-com#365 first), then a grounded legal-brief Studio | Grounded: no non-existent repository, no unresolved URL, no statute claim without a found quote or `TO_VALIDATE`. Ungrounded control: the invention is still observed |
| 8 | orazaka-ai-engine (+ packs#107) | Retire the `GET` context path of `DefaultMcpOrchestrator`; `McpClientFactory` (the client that already speaks MCP over SSE and stdio) gains Streamable HTTP, a client reused across requests, and an effect class per tool (`READ_ONLY`, `DRAFT`, `MUTATE_WITH_APPROVAL`); grounded research is offered `READ_ONLY` tools only | Integration test against a local MCP test server; a `MUTATE` tool is never offered in a grounded call; no `GET` to an MCP URL remains |

## Open points (TO VALIDATE)

- LégisQuébec: an honest bot user agent gets 403 (measured 2026-10-23). Ask the publisher for permitted automated access.
- Registraire des entreprises: the terms for automated access and its open data could not be read.
- SearXNG: hosting cost on the dev platform; upstream rate limits on a single instance (load test in slice 3).
- Brave: which plan carries storage rights.
- AGPL-3.0: running SearXNG unmodified as a separate service; our reading is that it does not reach our code. Counsel
  confirms before production.
