---
description: ARCHITECTURAL REVIEW GATE & REJECTION CRITERIA
---

# Workflow: Architectural Review Gate

## §0 Init
1. Load `.agent/rules/governance_boundary.md`
2. Load `.agent/rules/naming_conventions.md`
3. Load `.agent/rules/security_standards.md`
4. Load `.agent/rules/performance_standards.md`

## §1 Rejection Gates

### Java Backend Rejections
- **Inline Serialization**: Banned `ObjectMapper` try-catches in services. Use mappers/JPA converters.
- **Simulated Streaming**: Banned `Thread.sleep` splitting. Use `Flux<ChatResponse>`.
- **FQCN**: Use imports instead of inline full package names (e.g. `java.util.Set`).
- **Mutation Dup**: Banned write-then-read. Return domain records from write methods.
- **Anemic Records [ERR-042]**: Compact record constructors must handle nulls/validation, not services.
- **Read-Before-Write [ERR-043]**: Use DB constraints + `DataIntegrityViolationException`.
- **Engine Coupling [ERR-100]**: Engines cannot import `ToolRegistry` or `McpOrchestrator` (use interceptors).
- **N+1 [ERR-200]**: Repo calls in loops banned. Use `LEFT JOIN FETCH` or `@EntityGraph`.
- **Procedural Validation [ERR-103]**: No service-side null/bounds checks on DTOs.
- **Imperative Noise [ERR-104]**: Remove nested structures, empty catches, obvious comments.
- **UI Graph Bypass [ERR-106]**: Hardcoded UI/CLI features banned. Map from `OperationGraph`.
- **Sealed Pattern [ERR-107]**: Use switch expressions for `NodeState`, not `instanceof`.
- **Pseudo-Records [ERR-109]**: Classes with record-like fields but no getters must be `record`s.
- **Encapsulation Leak [ERR-110]**: Package-private for filters, interceptors, converters.
- **Polymorphic Sniffing [ERR-111]**: Regex type checks banned; use sealed classes.
- **Protocol Mixing [ERR-112]**: Isolate GraphQL, REST, AMQP in distinct sub-packs.
- **Env Sniffing [ERR-113]**: `Environment.getProperty` in beans banned; use `@ConfigurationProperties`.
- **Inline Mapping [ERR-114]**: Extract 5+ setter lines in controllers/services to `*Mapper`.
- **Prefix Violation [ERR-104]**: Banned prefixing with `Orazaka` (e.g. `OrazakaEngine` -> `Engine`).
- **Caller Validation [ERR-116]**: Services must trust record constructor invariants; no inline `if (request.field() != null)`.

### Frontend TypeScript Rejections
- **Hardcoded [ERR-115]**: No literal text in JSX/catch. Use `TranslationDictionary`.
- **Date Mutation [ERR-108]**: Banned native Date manipulation. Use `date-fns`.
- **File Length**: Max 250 lines for `.tsx`.
- **Inline Loops**: Map JSX loops must be sub-components.

### Python Rejections
- Type Hints: Required on all public signatures.
- Error Handling: No bare `except:` statements.
- Secrets: Hardcoded API keys banned; use environment.
- Logs: Banned `print()`. Use `logging`.
- Mutable Defaults: Banned `def f(x=[])`. Use `x=None`.
- Clean Code: No dead code or unused imports.
- Paths: Use `pathlib.Path`, not string concatenation.
- Pip: Banned runtime package installation; use `requirements.txt`.
- Resources: Use `with` context managers for files/sockets.
- Exit Codes: Always specify exit code via `sys.exit(code)`.

### Terraform Rejections
- Values: Inline strings in resources banned; use `var.*`.
- Description: All variables must have `description` blocks.
- Secrets: Must flag as `sensitive = true`.

## §2 Documentation Gate
- Public interfaces/methods require Javadoc.
- Shared React elements require TSDoc.
- Obvious or redundant comments must be deleted.

## §3 Code Quality & Formatting
- **Java**: `mvn spotless:apply` / `mvn spotless:check`
- **Frontend**: `npm run format` / `npm run lint` inside `orazaka-apps/ui` (workspace root)
- **Python**: `ruff check orazaka-apps/workers/ --fix`, `black orazaka-apps/workers/`
- **SonarQube** *(DEFERRED — target quality bar for the staging/cloud phase; not run in the local-only phase, AGENTS.md §0)*: `mvn verify sonar:sonar -Dsonar.token=$SONAR_TOKEN`.
  - Rejection Rules:
    - **S3776**: Cognitive Complexity > 15 (Critical)
    - **S1068** / **S1144**: Unused fields/methods (Major)
    - **S3655**: unchecked `Optional.get()` (Blocker)
    - **S5778**: Multi-invocations in `assertThrows` (Major)
    - **S106**: `System.out`/`System.err` in production (Major)
    - **S2221**: Catching general `Exception` (Major) (Exceptions allowed: MCP init, SSE loop, Bean fallback).
  - Metrics Gates: New Coverage >= 80%, Duplication <= 3%, Security/Reliability/Maintainability rating A.

## §4 Verification Pipeline Order
1. Build/edit files.
2. Format: `mvn spotless:apply` or `npm run format`.
3. Static scan: `mvn spotless:check` or `npm run lint`.
4. Compile: `mvn clean compile -pl orazaka-apps/orazaka-router -am`.
5. Architecture gate: `./mvnw test -pl engine/sovkora-engine-core -Dtest=GovernanceTest` (ArchUnit ring rules).
6. Test: `mvn test -q` or `npm test`.
7. **Sonar — DEFERRED in the local phase** (AGENTS.md §0: 100% local, no cloud/CI). `./mvnw verify sonar:sonar`
   is **not** part of the local gate; the SonarQube rules in §3 are kept as the *target* quality bar for the
   future staging/cloud phase, enforced locally only by `spotless` + `lint` + `GovernanceTest`.