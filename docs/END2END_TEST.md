---
category: Operations
order: 12
---

# Integration & E2E Testing Framework

> Comprehensive testing infrastructure: Unit, Governance (ArchUnit), Integration (Testcontainers), and E2E (Playwright & Vitest).
>
> **Scope**: The `e2e/sovkora-e2e` module validates **Java/Web Gateway API only**. Mobile (`orazaka-mobile-client`) is excluded from E2E — it runs lightweight TypeScript validation via the split-track CI pipeline.

---

## 1. Test Pyramid Architecture

```
                    ┌──────────────┐
                    │   Playwright  │  ← E2E (UI): Headless Chromium
                    │   Smoke Test  │     BFF SSE streaming mocks
                    └──────┬───────┘
                           │
                   ┌────────┴────────┐
                   │  Vitest + Execa  │  ← E2E (CLI): Temp directory
                   │  CLI Sandbox     │     sandboxed subprocesses
                   └────────┬────────┘
                            │
            ┌───────────────┴───────────────┐
            │   Spring Boot + Testcontainers │  ← Integration: Ephemeral Docker
            │   Singleton Container Pattern   │     @DynamicPropertySource ports
            └───────┬───────────────┬───────┘
                    │               │
       ┌────────────┴────────────┐ ┌┴──────────────────────────────────┐
       │  ArchUnit Governance     │ │ JUnit 5 + Mockito                 │  ← Unit
       │  GovernanceTest.java     │ │ Record constructors, algorithms  │     1:1 matching
       └─────────────────────────┘ └───────────────────────────────────┘
```

| Tier | Framework | Purpose | Location |
| :--- | :--- | :--- | :--- |
| **Unit** | JUnit 5 / Mockito | Business algorithms, constructor boundaries | `src/test/java/**/` (per module) |
| **Governance** | ArchUnit | Compile-time architecture decoupling rules | `GovernanceTest.java`, `*BoundaryTest.java` |
| **Integration** | Testcontainers | Ephemeral PostgreSQL, Redis, RabbitMQ | `AbstractContainerIntegrationTest` |
| **E2E (Java)** | Playwright Java | Headless Chromium with full gateway stack | `e2e/sovkora-e2e/src/test/java/` |
| **E2E (CLI)** | Vitest / Execa | Sandboxed CLI subprocess testing | `apps/cli/e2e/` |

---

## 2. Integration Testing (Testcontainers)

All integration suites extend `AbstractContainerIntegrationTest` (starts PostgreSQL, Redis, and RabbitMQ once per JVM process). Dynamic ports are resolved using `@DynamicPropertySource` to eliminate hardcoded defaults.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthControllerIntegrationTest extends AbstractContainerIntegrationTest {
    @LocalServerPort private int port;

    @Test
    void shouldReturnHealthyStatus() {
        assertThat(port).isPositive();
    }
}
```

- Run: `./mvnw clean verify` (Requires Docker).

---

## 3. ArchUnit Governance Rules

Enforced at compile time inside `GovernanceTest.java` (`orazaka-core` checks):
- `noWebControllersInCore`: Zero `@RestController` or Spring web imports.
- `noServletDependenciesInCore`: Zero `jakarta.servlet` leaks.
- `noAmqpDependenciesInCore` / `noRabbitMqDependenciesInCore`: Decouples AMQP/RabbitMQ.
- `recordsMustBePureDataCarriers`: Enforces record purity (no dependencies/IO inside records).

Additional boundary tests per module (`*BoundaryTest.java`) ensure cross-module isolation rules.

---

## 4. UI E2E Playwright Smoke Tests

- Dev server: Next.js on `http://localhost:3000`.
- Mocking: Intercepts network calls to isolate front-end tests:
  ```typescript
  await page.route('**/api/v1/chat/stream**', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: 'data: {"content":"Hello"}\n\ndata: {"content":" World"}\n\n',
    });
  });
  ```
- Run (via Maven): `./mvnw clean verify -P e2e-tests`.
- Run (standalone): `npm run test:e2e --prefix apps/web`.

---

## 5. CLI E2E Sandbox Tests

CLI tests run using Vitest inside a temporary operating system folder to avoid polluting the workspace root:
```typescript
import { execa } from 'execa';
const sandbox = fs.mkdtempSync(path.join(os.tmpdir(), 'orazaka-e2e-'));
const result = await execa('npx', ['orazaka', 'init'], { cwd: sandbox });
expect(result.exitCode).toBe(0);
```
- Run (via Maven): `./mvnw clean verify -P e2e-tests`.
- Run (standalone): `npm run test:e2e --prefix apps/cli`.

---

## 6. Split-Track CI Pipeline

The CI pipeline uses `dorny/paths-filter` to separate backend/web validation from mobile validation:

```
Track A (always runs on push/PR):
[Phase 1: Lint / Spotless] ──► [Phase 2: Java Testcontainers] ─┬─► [Phase 5: SonarCloud]
                           ──► [Phase 3: JS/TS Unit Tests]   ─┘─► [Phase 4: Playwright E2E]

Track B (path-triggered: orazaka-mobile-client/, orazaka-shared/, workspace package.json):
[Mobile Validation: ESLint + tsc --noEmit]
```

> **Note**: `frontend-maven-plugin` in the root `pom.xml` runs `npm ci` and `npm run build` during the Maven lifecycle — web targets only. Mobile is explicitly excluded via `--ignore=orazaka-mobile-client`.

---

## 7. e2e/sovkora-e2e Module Architecture (ADR-040)

Dedicated Maven submodule at `e2e/sovkora-e2e/` housing the complete hermetic E2E testing matrix.

### Sequential Execution Timeline

```
pre-integration-test:
  1. Fabric8 → compose start db-local (PostgreSQL, TCP:5432 wait)
  2. process-exec:start → java -jar orazaka-conversation-service-*.jar (background)
  3. Antrun → poll /actuator/health until 200 UP (120s timeout)
  4. frontend-maven-plugin → install Node v24.11.1

integration-test:
  Tier 1: npx httpyac send src/test/resources/api-contracts/ --all --bail
  Tier 2: npm run test:e2e (apps/cli)
  Tier 3: Failsafe → *IT.java (Playwright Java, headless Chromium)

post-integration-test:
  process-exec:stop-all → kill gateway process
  Fabric8 → stop + remove db-local

verify:
  Failsafe → verify goal (fail build on test failures)
```

### E2E Test Classes

| Class | Tier | What it validates |
| :--- | :---: | :--- |
| `GatewayHealthIT` | 3 | Actuator health endpoint via headless Chromium |
| `RegistrationIT` | 3 | User signup, email verification, duplicate handling |
| `ChatSessionIT` | 3 | Full user journey: input → submit → streaming tokens → unlock |
| `ProfilePasswordIT` | 3 | Password change, recovery flow, token expiry |
| `UserSettingsIT` | 3 | Preferences CRUD, theme switching, model selection |
| `PlaygroundMediaIT` | 3 | Image/video/audio generation pipelines via REST |
| `AdminDashboardIT` | 3 | Admin-only model catalog CRUD, pipeline reset, job management |

### E2E Utility Clients

| Class | Purpose |
| :--- | :--- |
| `E2eJdbcClient` | Direct JDBC queries for database state verification |
| `E2eAmqpClient` | RabbitMQ queue inspection and message assertions |
| `E2eRedisClient` | Redis key/value verification for cache and rate-limit state |

### API Contract Files (`src/test/resources/api-contracts/`)

| Contract | Purpose |
| :--- | :--- |
| `health.http` | Actuator health probe |
| `auth.http` | Login, register, forgot/reset password flows |
| `models.http` | Model catalog listing endpoint |
| `operations.http` | Chat and operations API |
| `chat-sessions.http` | Chat session lifecycle management |
| `media-generation.http` | Image, video, and audio generation endpoints |
| `user-settings.http` | User preferences and settings CRUD |
| `admin-pipeline.http` | Admin pipeline configuration and reset |

---

## Related Documentation
- [Developer Onboarding Guide](101.md)
- [Architecture Reference](_generated/ARCHITECTURE.md)
