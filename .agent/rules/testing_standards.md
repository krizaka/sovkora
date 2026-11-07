# Rule: Testing Standards (local)

> Local phase: no deployed environments, so **hermetic E2E** is the quality gate — and CI runs every tier on each
> pull request. **No test, not done; a fix starts with a failing test.** See `AGENTS.md` §9.

## §1 Pyramid
- **unit**: pure logic, mappers, self-validating records. No external dependency. 1 test file per type (`[ERR-103]`).
- **integration**: adapters/repos via **Testcontainers**. Extend `AbstractContainerIntegrationTest` (PostgreSQL + Redis + RabbitMQ bootstrapped **once per JVM**, reused singleton). Dynamic ports via `@DynamicPropertySource` (ADR-033/034).
- **hermetic E2E**: `e2e/sovkora-e2e`, end-to-end journeys on throwaway infra (seed). Command: `orazaka test e2e`.

## §2 ArchUnit (mandatory gate)
- `GovernanceTest` validates the ring rules on every build.
- Pre-commit: `./mvnw test -pl engine/sovkora-engine-core -Dtest=GovernanceTest`.
- Any hexagonal boundary violation (forbidden imports, `.md` in `core/resources/prompts`, etc.) must fail this test.

## §3 Discipline
- Every new port → a contract test. Every new adapter → a Testcontainers integration test.
- Banned: tests depending on the public network or a cloud service (incompatible with the local phase).
- AMQP consumer idempotency: covered by a test replaying the same `messageId`.
