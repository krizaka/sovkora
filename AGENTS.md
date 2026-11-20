# Sovkora — agent contract

<!-- krizaka-agents-template v2: rendered from krizaka/.github docs/standards/agents-template.md (scripts/agents-md.mjs). The one AGENTS.md standard: six sections in this order, at most 150 lines, bullets and imperatives. The conformance check « agents-template » reads the headings, the length and CLAUDE.md. Detail goes to docs/ via References, not here. -->

## Project context

- Sovkora is the AI platform of Krizaka: one repository (ADR-029), every module a folder listed in build order in [modules.json](modules.json). Java 21 / Spring Boot 4 (engine, services, contracts), Next.js / Expo / Tauri / CLI under `apps/`, a Python media worker (uv) under `workers/`.
- Kind: `product` · team: `sovkora` · registry entry: [repos.json](https://github.com/krizaka/.github/blob/main/repos.json).
- Read this file first, then the section of [docs/agents.md](docs/agents.md) for what you touch (it is the full former contract, numbered §0–§13; code and docs cite its §N).
- `CLAUDE.md` is the one line `@AGENTS.md`: never a second copy of it.

## Essential commands

- Java, whole reactor: `./mvnw verify`; one module: `./mvnw test -pl <role>/<module>` (e.g. `engine/sovkora-engine-core`).
- Architecture rules before a push: `./mvnw test -pl engine/sovkora-engine-core -Dtest=GovernanceTest`; static codemods: `./mvnw rewrite:dryRun`.
- Clients: `pnpm install`, then per app (`apps/web`, `apps/cli`, `apps/mobile`): `pnpm run lint`, `pnpm test`, `pnpm run format:check`.
- Python worker (`workers/media`): `uv run ruff check . && uv run ruff format --check . && uv run pytest`.
- Layout and names: `node scripts/check-java-layout.mjs`, `node scripts/check-code-names.mjs`, `node scripts/check-krizaka-versions.mjs`.
- Docs from code: `node scripts/generate-docs.mjs` (commit `docs/_generated`; `--check` fails when stale). Hermetic E2E: `sovkora test e2e`.
- Run them on what you changed before every push; CI runs the rest.

## Architecture & conventions

- Follow the [krizaka-engineering skill](https://github.com/krizaka/krizaka-skills/blob/main/skills/krizaka-engineering/SKILL.md); search its [packages reference](https://github.com/krizaka/krizaka-skills/blob/main/skills/krizaka-engineering/references/packages.md) before writing anything generic.
- Keep hexagonal ports and adapters (ArchUnit): dependencies point toward `engine/sovkora-engine-core`, which holds no business rule and no web/DB type; a port lives where it is needed, its adapter is package-private in an outer module.
- Keep the layers: ingress (`conversation`, `edge`) translates transport to an `Intention` only; `business` is thin coordination and holds no LLM logic; the agent loop lives in the core; the async executor is `job`.
- Depend only on modules listed before you in `modules.json`; another context's Tier-3 (`business`, `persistence`, identity) is never a dependency — use its `-api` or `-client` ([SEAM-002]).
- Name Java by role: artifact `sovkora-<module>[-<part>]`, package `com.krizaka.sovkora.<module>`, no product prefix on classes, `<Resource>Controller`, `<Capability>Service`, `<Tech><Port>Adapter`; never `*Utils`, `*Helper`, `*Manager`, `*Orchestrator`, `*Impl` in `-common`.
- Use self-validating records (validation in the compact constructor), `RestClient`/`@HttpExchange`, virtual threads; no `synchronized` around I/O, no raw SQL, no read-before-write.
- Persist per bounded context: no JPA entity outside the persistence modules, no cross-context FK, bootstrap SQL in the owning module's `infra/initdb/`.
- Messaging on RabbitMQ is for heavy or deferred work; chat streaming stays synchronous. A job is produced only by the studio outbox (details: docs/agents.md §6).
- Web: browser traffic goes through the Next.js BFF, never straight to `:8080`; native clients call the edge (`:8088`). Types come from `@krizaka/orazaka-shared`; `.tsx` ≤ 250 lines; dates with `date-fns`.
- Hierarchy: this root file holds the rules of the whole repository; a sub-folder with its own rules has a local `AGENTS.md` that says only what differs ([apps/web/AGENTS.md](apps/web/AGENTS.md)). The nearest file wins.

## Guard-rails & known pitfalls (read before coding)

- The checks are the judge ([ADR-015](https://github.com/krizaka/krizaka-internal/blob/main/docs/adr/.github/ADR-015-ai-code-audit-measured-and-enforced.md), [guards](https://github.com/krizaka/.github/blob/main/docs/standards/guards.md)): never `--no-verify`, never a new `eslint-disable`, never a raised ratchet, baseline or `archunit_store/` entry; a rule is never silenced, skipped or weakened to get green.
- Local-first: no cloud deployment other than `dev` (docs/DEPLOY_DEV.md), no new network dependency; inference runs native on macOS, never in Docker.
- No shell scripts for setup or operations; the `sovkora` CLI is the orchestrator ([ERR-125]).
- Declared, not inferred: a component that needs a fact about another's work reads a declared marker or typed field and fails closed without it; never deduce it from a name or a shape (docs/agents.md §12).
- Repairing an inert control is a change: trace its never-run branch before making it reachable (kill-switch ADR-062: it refuses the turn, never bypasses the pipeline).
- Secrets: two distinct token keys, no default in any `application*.yml` ([CFG-002]); Spring Boot stays on the pinned `4.0.x` line and BOM-managed versions are never bumped one by one; never `npm audit fix --force`.
- Generated docs (`docs/_generated`) are never hand-edited; rerun the generator after a change to an interface, a config key, an `initdb` SQL or an env variable.
- Anti-slop:
  - No defensive null on `@NullMarked` code (NullAway); a new package is `@NullMarked`.
  - A closed set is a type (enum, sealed interface), never a string compared by hand.
  - HTTP in Java is a DTO record, `@Valid` and `ProblemDetail`: no `Map.of("error", …)`, no entity in a response, one `@RestControllerAdvice` per service.
  - Use `?.` and `??` in TypeScript; React data comes from `useQuery`/`useMutation` of `@krizaka/query`, never `useEffect`.
- Start a fix with a failing test that stays as the guard; every bug gets its guard; a « check then write » runs in one transaction with a lock and has a concurrency test.
- A business rule is enforced in the service, so no route skips it; a cleanup never masks the original error; validate the original input, not a trimmed copy.
- Every user-facing string is in the FR and EN messages, no hard-coded colour; environment variables are `<CONTEXT>_<THING>`, declared once.
- Never invent a URL, a law article, a price or a figure you did not read; write `TO VALIDATE` and say who decides.
- Never commit a secret, a capture or a log; no production data in a session, only the dev data.
- Keep the edit hook in `.claude/settings.json`: `npx --no-install krizaka-edit-check` on `Edit|Write`.

## Expected validation workflow

- [ ] Compile, format, lint and test what changed (the commands above); fix the cause of a red, never the guard.
- [ ] The « before you push » checklist of the skill and docs/agents.md « Before you open or update a pull request » hold for what changed.
- [ ] `GovernanceTest` is green; `node scripts/generate-docs.mjs` has run if interfaces, config or migrations changed.
- [ ] A feature or fix PR names its journey (`Journey: <name>` or `Journey: n/a — <reason>`); every review thread is answered.
- [ ] README and generated docs still say what the code does.
- [ ] One issue, one small PR (`Closes #<n>`); merge only with green CI.

## References

- [docs/agents.md](docs/agents.md): the full former contract by section (phase, CLI, modules and tiers, naming, backend, persistence, messaging, interceptors, clients, testing, docs generation, security, declared-not-inferred, OpenRewrite/ArchUnit, session protocol).
- [docs/VISION_ARCHITECTURE.md](docs/VISION_ARCHITECTURE.md), [docs/INTERFACES.md](docs/INTERFACES.md), [docs/DEVEX_LIFECYCLE.md](docs/DEVEX_LIFECYCLE.md), [docs/DELIVERY.md](docs/DELIVERY.md), [docs/NAMING.md](docs/NAMING.md), [docs/DEPLOY_DEV.md](docs/DEPLOY_DEV.md).
- [Org standards](https://github.com/krizaka/.github/tree/main/docs/standards) of krizaka/.github; the [engineering charter](https://github.com/krizaka/.github/blob/main/AGENTS.template.md).
- [krizaka-engineering skill](https://github.com/krizaka/krizaka-skills/blob/main/skills/krizaka-engineering/SKILL.md).
