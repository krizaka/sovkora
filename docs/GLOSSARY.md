---
title: Glossary
description: Documentation for Glossary
category: Guidelines
order: 16
---
# Glossary

> Quick reference for Sovkora ecosystem terms, patterns, themes, and environment variables.

---

## 1. Core Terms & Patterns

- **Bridge Pattern 2.0**: Core model wrappers ensuring Spring AI types do not leak outside `orazaka-core`.
- **Ports & Adapters**: Decouples business logic (`core` ports) from framework details (`gateway`, `tools` adapters).
- **Context-Matrix Pipeline**: 15-stage ordered filter chain enriching prompt context.
- **Multi-Tier Caching**: Caching tool outputs locally in Caffeine (L1) and PostgreSQL (L2).
- **Server-Driven UI (SDUI)**: Client layout capabilities dynamically driven by backend `OperationGraph` states.
- **Mapper Isolation (ERR-107)**: Package-private `*Mapper` files containing static construction mapping.
- **Immutable Time (ERR-108)**: Enforces `date-fns` and ISO-8601 strings, banning native Date mutations.
- **Input Blocking Invariant (ERR-126)**: Disables text fields, submit buttons, and menus during streaming.
- **Self-Validating Records (ERR-106)**: Java records validate fields in compact constructors.
- **Owner Validates Field (ERR-116)**: Record/class owning a field validates it. No inline checks in services.
- **BFF Proxy**: Browser never hits Gateway (8080) or Ollama (11434) directly. All traffic proxies via Next.js server API routes.
- **Frontend Maven Integration**: `frontend-maven-plugin` runs `npm ci` and `npm run build` inside the Maven lifecycle — web targets only, mobile bypassed via `--ignore=orazaka-mobile-client`.
- **Split-Track CI**: Track A (backend+web) always runs the full 5-phase pipeline. Track B (mobile) triggers only on path changes — lightweight ESLint + `tsc --noEmit`.

---

## 2. Environment Variables

All variables are defined in the root `.env` file (loaded by `properties-maven-plugin` at the `initialize` phase). Frontend-specific variables are in `apps/web/.env.local`.

### Infrastructure & Database

| Key | Default | Purpose |
| :--- | :--- | :--- |
| `PORT` | `8080` | Spring Boot Gateway listen port |
| `GATEWAY_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Allowed CORS origins for BFF proxy |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/orazaka_db` | Main PostgreSQL connection string |
| `SPRING_DATASOURCE_USERNAME` | `orazaka_admin` | PostgreSQL database user |
| `SPRING_DATASOURCE_PASSWORD` | `orazaka_secure_pass` | PostgreSQL database password |
| `REDIS_URL` | `redis://localhost:6379` | Redis rate-limiting and session cache |

### Message Broker (RabbitMQ)

| Key | Default | Purpose |
| :--- | :--- | :--- |
| `SPRING_RABBITMQ_HOST` | `localhost` | RabbitMQ host address |
| `SPRING_RABBITMQ_PORT` | `5672` | RabbitMQ listen port |
| `BROKER_QUEUE_MAX_LENGTH` | `1000` | Max RabbitMQ queue capacity |
| `CB_FAILURE_RATE` | `50` | Circuit breaker failure threshold percentage |

### AI Providers (Ollama)

| Key | Default | Purpose |
| :--- | :--- | :--- |
| `DEFAULT_PROVIDER` | `ollama` | Active AI provider (bound to `orazaka.core.default-provider`) |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Local Ollama service URL |
| `OLLAMA_MODEL` | `llama3.2:3b` | Default chat model |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text:latest` | Local embedding model for RAG |
| `ROUTER_PROVIDER` | `ollama` | Provider for RouterInterceptor intent classification |
| `ROUTER_MODEL` | `llama3.2:3b` | Model for RouterInterceptor |

### Workers & Media

| Key | Default | Purpose |
| :--- | :--- | :--- |
| `VIDEO_WORKER_PORT` | `8188` | SVD video worker probe port |
| `IMAGE_WORKER_PORT` | `8085` | stable-diffusion.cpp local port |
| `IMAGE_GEN_SEED` | `-1` | Image generation seed (`-1` = random) |
| `VIDEO_GEN_SEED` | `-1` | Video generation seed (`-1` = random) |
| `LOCALAI_BASE_URL` | `http://localhost:8085` | LocalAI image/TTS service endpoint |
| `LOCALAI_API_KEY` | `not-required` | LocalAI API key (not needed for local usage) |
| `LOCALAI_MODEL` | `tts-1` | Default LocalAI model |

### Security & Cryptography

| Key | Default | Purpose |
| :--- | :--- | :--- |
| `CRYPTO_KEY` | *(required)* | AES-256 JPA field encryption key |
| `CRYPTO_SALT` | *(required)* | AES-256 encryption salt (hex) |

### Timeouts

| Key | Default | Purpose |
| :--- | :--- | :--- |
| `ASYNC_TIMEOUT_MS` | `600000` | Async operation timeout (10 minutes) |
| `JOBS_TIMEOUT_SECONDS` | `600` | Background job execution timeout |

### Uploads & Logging

| Key | Default | Purpose |
| :--- | :--- | :--- |
| `UPLOAD_DIR` | `var/orazaka-uploads` | File upload storage directory |
| `LOG_DIR` | `var/logs` | Application log output directory |
| `GATEWAY_LOG` | `var/logs/gateway.log` | Gateway-specific log file path |

### Frontend BFF (Next.js) — `apps/web/.env.local`

| Key | Default | Purpose |
| :--- | :--- | :--- |
| `NEXTAUTH_SECRET` | *(required)* | BFF session encryption cookie key |
| `NEXTAUTH_URL` | `http://localhost:3000` | UI domain for NextAuth callbacks |
| `GATEWAY_URL` | `http://localhost:8080` | Internal Gateway API proxy target |
| `NEXT_PUBLIC_UI_URL` | `http://localhost:3000` | Public UI base URL |

### SonarQube

| Key | Default | Purpose |
| :--- | :--- | :--- |
| `SONAR_TOKEN` | *(CI secret)* | SonarCloud authentication token |

### E2E Testing (ADR-040)

| Key | Default | Purpose |
| :--- | :--- | :--- |
| `DB_LOCAL_PORT` | `5433` | Ephemeral E2E PostgreSQL port |
| `DB_LOCAL_NAME` | `orazaka_e2e` | E2E database name |
| `DB_LOCAL_USERNAME` | `orazaka_e2e` | E2E database user |
| `DB_LOCAL_PASSWORD` | `orazaka_e2e_pass` | E2E database password |
| `GATEWAY_TARGET_URL` | `http://localhost:8080` | Gateway target for E2E contract tests |

### Test Credentials

| Key | Default | Purpose |
| :--- | :--- | :--- |
| `ADMIN_EMAIL` | `admin@orazaka.com` | Seeded admin account email |
| `ADMIN_PASSWORD` | `admin` | Seeded admin account password |
| `USER_EMAIL` | `user@orazaka.com` | Seeded user account email |
| `USER_PASSWORD` | `user` | Seeded user account password |

---

## 3. UI Accents & Themes

UI theme accents are resolved from the database (`theme` category in `orazaka_models`), decoupling display styles from hardcoded configurations.
- **Accents**: `zinc` (default), `rose`, `emerald`, `amber`.
- **Flow**: User settings choice → `themeAccent` saved in preferences → Next.js client `useTenant()` context mapper resolves colors via `accentMap`.

---

## 4. ERR Code Reference

| Code | Rule | Summary |
| :--- | :--- | :--- |
| ERR-102 | Module Separation | Strict module boundary enforcement |
| ERR-103 | Structuring | One top-level class per file, 1:1 test matching |
| ERR-104 | Naming | Banned: prefixing classes with `Sovkora` or `orazaka-` |
| ERR-106 | Domain Contracts | Self-validating records, compact constructor validation |
| ERR-107 | Mapper Isolation | Mapping logic in package-private static `*Mapper` files |
| ERR-108 | Date Formatting | `date-fns` only. Banned: moment.js, dayjs, native Date methods |
| ERR-109 | Persistence | Parameterized repos, no raw SQL, catch `DataIntegrityViolationException` |
| ERR-112 | Segregated Ingress | GraphQL, REST, AMQP in separate sub-packs |
| ERR-116 | Owner Validates | Record owning field validates it |
| ERR-120 | Clients | Spring `RestClient` or `@HttpExchange` only. Banned: `java.net.http.HttpClient` |
| ERR-122 | Core Interceptor Purification | Only `PromptContextInterceptor` interface in core interceptor package |
| ERR-125 | CLI Invariant | No shell scripts. Must run via `npx orazaka` subcommands |
| ERR-126 | Input Blocking | Text entry locked when `isSending \|\| isGenerating` |
| ERR-130 | Local Agent Protocol | SQLite task store, heartbeat sync, SSE tunnel |
| ERR-135 | Record Purity | Records are stateless data carriers — no loggers, I/O, Spring beans, or service facades |

---

## Related Documentation
- [Developer Onboarding Guide](101.md)
- [Architecture Reference](_generated/ARCHITECTURE.md)
- [API Reference](_generated/API_REFERENCE.md)
