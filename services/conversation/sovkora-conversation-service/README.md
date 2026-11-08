# Conversation service

> `services/conversation/sovkora-conversation-service` of [Sovkora](../../../README.md), one repository (maven, ai layer). Depends on `build`, `contracts`, `engine`. Licence: [FSL-1.1-ALv2](../../../LICENSE).

Interactive ingress of the Sovkora engine: chat (SSE streaming), intents, models, pipeline, MCP and job APIs — translates transport into Intentions, never business logic.

## What it provides

The interactive ingress (port `8080`, behind the edge): `/api/v1/chat` (SSE streaming),
`/api/v1/intent`, `/api/v1/models`, `/api/v1/pipeline`, `/api/v1/mcp`, `/api/v1/checks` (court records and credit for products, sovkora-engine#105), `/api/v1/jobs`,
`/api/v1/features`, `/api/v1/media`, `/api/v1/assets`, `/api/v1/status`.
Service-only: `POST /internal/v1/agent-turns` (one model turn of a client-run agent for the Studio service, ADR-093 §4: the actor is named in the body, hydrated and refused when unknown or disabled, the engine returns the tool calls instead of running them).
It translates transport into an `Intention` and enforces security — **no business logic**.
Owns its schema: `src/main/resources/db/migration/V1__conversation_baseline.sql`, applied by Flyway at startup (history table `flyway_history_conversation`; `infra/initdb/20-conversation.sql` is a comment).

### The law review queue (ADR-089 §8): `/api/v1/admin/law/**`

The routes behind the console's **Law → Proposals** screen. They are for **administrators only**: a session without
`ROLE_ADMIN` gets 403. **Every write leaves an `operator_audit` line** (ADR-085), refused ones included. Every rule
belongs to the sovkora-engine use cases; these controllers only translate HTTP.

| Method and path | Use case |
|:---|:---|
| `GET /proposals?status=&kindId=` · `GET /proposals/{id}` | `RuleProposalService.list` / `find` |
| `PUT /proposals/{id}` (a rule) | `RuleProposalReviewService.edit`: the next revision, vetted again |
| `POST /proposals/{id}/approve {note}` · `POST /proposals/{id}/reject {reason}` | `RuleProposalService.approve` / `reject` |
| `GET /kinds/{kindId}/golden` | `RulesetPromotionService.goldenCases` |
| `POST /kinds/{kindId}/promote {proposalIds, effectiveFrom, revisionNote?}` → `{prUrl, …}` | `RulesetPromotionService.promote`: opens the sovkora-packs pull request, or answers 409 with its `code` |
| `POST /derivations {jurisdiction, kindId, asOf, language?}` → `{runId, …}` | `KindDerivationService.start` (503 `law-derivation-off` when it is not wired) |
| `GET /demand` | `KindDemandService.list` |
| `GET /instruments` · `GET /articles?jurisdiction&instrument&ref&language&asOf` · `GET /imports?limit=` | `LawCorpusCatalogueService`: metadata only, never an article's text |
| `POST /imports` (multipart: `file`, `jurisdiction`, `instrument`, `language`, `datasetUrl`, `sha256?`, `consolidatedTo?`) | `LawManualImportService.importFile` (503 `law-corpus-disabled` while the corpus is off) |

Refusals are Problem Details with a `code` slug. The 409 codes are:
- `proposal-not-reviewable`, `proposal-not-editable`, `proposal-not-approved`;
- `already-in-review`, `kind-mismatch`, `duplicate-rule`;
- `golden-minimum-not-met`, `law-version-mismatch`, `quote-too-long`.

The other refusals are 404 `proposal-not-found` / `kind-not-found`, 503 `law-promotion-off`, and 400 `invalid-request`.

Promotion is off by default. To turn it on, set these variables (read in `META-INF/sovkora/conversation-service-defaults.yml`):

| Variable | Purpose |
|:---|:---|
| `AI_LAW_PROMOTION_ENABLED` | `true` turns promotion on. |
| `APP_GITHUB_AGENTS_APP_ID` | The id of the GitHub App `krizaka-agents`. |
| `APP_GITHUB_AGENTS_INSTALLATION_ID` | The App's installation. |
| `APP_GITHUB_AGENTS_PRIVATE_KEY` | The App's private key, in PEM. |

Promotion never uses a personal token.

**Rule `[AUDIT-001]`** (`AdminWritesAreAuditedTest`): every POST, PUT, PATCH or DELETE mapping under `/api/v1/admin` is on the
audit filter's operator surface (`OperatorAuditPaths`). The rule is proven on a planted controller. The audit line names a
mapped write by its template (`POST /api/v1/admin/law/kinds/{id}/promote`), and its target is the template's last variable.

## Build and test

```bash
./mvnw verify -pl services/conversation/sovkora-conversation-service -am      # from the repository root: this module and what it needs
./mvnw verify -pl services/conversation/sovkora-conversation-service -amd     # this module and everything that depends on it
```

CI runs it on every pull request that touches `services/conversation/sovkora-conversation-service/` (ADR-029: one repository, one CI).
