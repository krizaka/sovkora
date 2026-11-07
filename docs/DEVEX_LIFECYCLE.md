---
title: "Orazaka — DevX, environments & lifecycle"
description: "Repo organization for the target, dev environment (macOS/Docker/native, IntelliJ+VSCode, orazaka CLI), E2E tests dev/staging/prod, docs generation to the Krizaka site, and deployment."
category: Guidelines
order: 3
---

# Orazaka — DevX, environments & lifecycle

> Operational closure of the vision: **repo organization**, **dev environment**, **E2E tests across 3 environments**, **docs generation** to the Krizaka site, and **deployment**.
> Dev hardware target: **macOS 64 GB** · Docker for infra · AI runtimes native (Metal). IDEs: **IntelliJ** (Java backend) + **VSCode** (TS / Python frontends). Single orchestrator: **the `orazaka` CLI**.

> [!IMPORTANT]
> **Current phase = 100% LOCAL.** No CI, no cloud deployment. Everything (build, run, test, docs) runs on the dev machine.
> The **staging / prod / CI** sections below describe the **deferred target**: they are **not** implemented until explicitly requested. What applies today: the **dev** column of the tables, **hermetic E2E** as the quality gate, and `orazaka docs build|sync` run **locally**. Enforceable rule: `.agent/rules/local_dev.md`.

---

## 1. Central principle — "one difference per environment: the config"

The code is **identical** everywhere. What changes from one environment to another is **the configuration and the infrastructure adapter**, never the domain. In particular:

- The **inference runtime** is native macOS in dev (Metal), but **containerized / cloud GPU** in staging/prod. Thanks to hexagonal, it's a simple **adapter swap** behind the `ChatGeneratorClient` port (Ollama localhost in dev; remote GPU endpoint in staging/prod). **"Native local-first" is a DevX optimization, not a prod constraint.**
- *Stateful* infra is `docker-compose` in dev, **Terraform** (managed) in staging/prod.
- Apps are **native processes launched by the CLI** in dev, and **the same Docker images promoted** in staging then prod.

| | **dev** (macOS 64 GB) | **staging** | **prod** |
| :--- | :--- | :--- | :--- |
| Apps (router, worker) | native processes via `orazaka dev` | Docker images | **same images promoted** |
| Infra (PG/pgvector/Redis/RabbitMQ) | local `docker-compose` | Terraform (managed) | Terraform (managed) |
| AI runtime | **native Metal** (Ollama/SD/SVD) | containerized GPU / Modal | Modal · RunPod *(or self-host GPU)* |
| Frontends | dev servers (Next/Expo) | deployed build | deployed build |
| Data | throwaway (seed) | anonymized | real |
| Spring profile | `application-dev.yml` | `application-staging.yml` | `application-prod.yml` |
| Tests | **hermetic** E2E (Testcontainers) | **full E2E suite** post-deploy | **smoke / synthetic** read-only |

---

## 2. Schema — structure & target (build once, promote)

```mermaid
flowchart TB
    subgraph REPO["orazaka monorepo — build once"]
        direction LR
        MVN["Maven · backend (router, framework, Java workers)"]
        NPM["npm workspaces · orazaka-ui (web/admin/mobile/cli)"]
        PY["Python · worker-media (MLX)"]
        DOCS["docs/ → generation"]
    end
    REPO --> ART["Artifacts: router image · worker image · front bundles · doc pages"]

    ART --> DEV
    ART --> STG
    ART --> PRD

    subgraph DEV["① dev — macOS 64 GB"]
        DAPP["apps: native processes (orazaka dev)"]
        DINF["🐳 docker-compose: PG·pgvector·Redis·RabbitMQ"]
        DAI["⚡ native Metal: Ollama·SD·SVD"]
    end
    subgraph STG["② staging — cloud (Terraform)"]
        SAPP["apps: Docker images"]
        SINF["managed infra"]
        SAI["containerized GPU / Modal"]
    end
    subgraph PRD["③ prod — cloud (Terraform)"]
        PAPP["same images promoted"]
        PINF["managed infra"]
        PAI["Modal · RunPod (or self-host GPU)"]
    end

    DEV -->|"promote"| STG -->|"promote"| PRD

    NOTE["same code · the inference adapter is swapped per profile (ChatGeneratorClient port)"]
    DAI -.- NOTE
    SAI -.- NOTE
    PAI -.- NOTE
```

---

## 3. Dev environment

### 3.1 What runs where (macOS 64 GB)

- **Docker** (*stateful* infra only): PostgreSQL+pgvector, Redis, RabbitMQ. Started by `orazaka start`.
- **Native macOS** (Metal): Ollama (chat+embeddings), stable-diffusion.cpp (image), worker-media MLX (video). Never in Docker (otherwise CPU fallback = destroyed DevX).
- **Apps**: the eight JVM services (edge, conversation, identity, knowledge, automation, job, billing, studio), the workers, web-client (with its operator console), mobile (Expo) — launched as native processes by `orazaka dev` with color logs.
- 64 GB lets everything run in parallel (Docker infra + 2–3 loaded Ollama models + apps + IDE).

### 3.2 IDE split

| IDE | Scope | Why |
| :--- | :--- | :--- |
| **IntelliJ IDEA** | Java/Maven backend: `orazaka-router`, `sovkora-libs/*`, Java workers | multi-module Maven support, Spring, JVM debugging, ArchUnit |
| **VSCode** | TS frontends (`orazaka-ui/*`), `worker-media` (Python), infra scripts | Next/Expo, ESLint, Pyright, integrated terminal for the CLI |

Both share the repo; **the `orazaka` CLI is the neutral orchestrator** both call. To commit so both IDEs are *turnkey*:

- `.run/` — IntelliJ run configs (Router [dev], Worker [dev], with `SPRING_PROFILES_ACTIVE=dev`).
- `.vscode/{launch,tasks,extensions}.json` — `orazaka dev` tasks, Next/Expo debugging, recommended extensions.

### 3.3 Local cycle (inner loop)

```bash
orazaka install      # detect HW/prereqs (Java 21, Node 22+, Docker, Ollama), generate .env
orazaka start        # start the Docker infra + check native Ollama
orazaka models pull  # pull the required Ollama models
orazaka dev          # spawn the 8 services + web + admin + mobile (color logs)
#   the 8: edge :8088 · router :8080 · identity :8083 · knowledge :8084
#          automation :8082 · job :8090 · billing :8095 · studio :8096
#   variants: orazaka dev --only web,router   |   --only studio,billing
#             orazaka dev --skip-studio       (one --skip-<key> per service)
orazaka onboard      # validate a clone-and-run (prereqs → build → M2M → runtime)
```

---

## 4. E2E tests across dev / staging / prod

A 4-level pyramid, driven by `e2e/sovkora-e2e` (matrix: httpyac API → Vitest CLI → Playwright UI → 4-tier validation).

```mermaid
flowchart LR
    U["unit (per module)"] --> IT["integration (Testcontainers)"] --> E2E["E2E (e2e/sovkora-e2e)"] --> SMK["smoke / synthetic"]

    subgraph DEVT["dev / CI — on every PR"]
        U
        IT
        E2E
    end
    subgraph STGT["staging — post-deploy"]
        E2Efull["FULL E2E suite (real API + UI)"]
    end
    subgraph PRDT["prod — post-deploy + scheduled"]
        SMK2["read-only smoke · synthetic monitoring"]
    end
    E2E -.->|"promote"| E2Efull -.->|"promote"| SMK2
```

| Level | Where | What | Data |
| :--- | :--- | :--- | :--- |
| Unit | dev + CI (every PR) | pure logic, mappers, records | none |
| Integration | dev + CI | repos/adapters via **Testcontainers** (reused singleton) | ephemeral |
| Hermetic E2E | dev + CI | end-to-end journeys, throwaway infra | seed |
| Full E2E | **staging**, post-deploy | **real** deployed API + UI | anonymized |
| Smoke / synthetic | **prod**, post-deploy + cron | non-destructive probes, read-only | real |

Commands: `orazaka test unit | it | e2e`. In CI, hermetic E2E gates the merge; full E2E gates promotion to prod.

---

## 5. Docs generation → Krizaka informational site

The docs are **generated then injected** into the org's Next.js site (already in place: `lib/docs.ts` reads `orazaka-content/docs`, frontmatter `title/description/category/order`).

```mermaid
flowchart LR
    subgraph SRC["Sources of truth (orazaka repo)"]
        CUR["curated docs/ (md + mermaid)"]
        CODE["code: UseCase registry · ADR · interceptor registry · interface contracts"]
    end
    CODE -->|"orazaka docs build (generates md)"| GEN["generated docs/"]
    CUR --> AGG["aggregate docs/"]
    GEN --> AGG
    AGG -->|"orazaka docs sync (copy)"| CONTENT["krizaka-com / orazaka-content/docs"]
    CONTENT -->|"lib/docs.ts + frontmatter"| SITE["Krizaka site (Next.js) — render + nav"]
```

- **`orazaka docs build`**: generates from the code **(a)** markdown (product catalog from the `UseCaseDescriptor`s, ADR ledger, interceptor registry, interface contracts) **and (b)** a **structured model** `docs/_generated/architecture.json` (modules, ports, dependencies, pipeline, messaging) extracted by ArchUnit/annotation scan. `--check` fails the build if the model is stale.
- **`orazaka docs sync`**: copies `products/orazaka/docs` + `docs/_generated/*` → `orazaka-content/docs` (consumed by the site).
- **Schemas = 3D ultra-design components**: on the Krizaka site, architecture views are rendered by **interactive 3D components** (React Three Fiber / Three.js + drei) driven by `architecture.json` — cinematic dark-mode, depth, zoom/rotate/hover. Mermaid is only a print fallback. *(Local phase: run locally, not in CI.)*
- The **frontmatter** (already present on our docs) drives navigation and ordering on the site. **Generated docs are never hand-edited.**

---

## 6. Deployment

### 6.1 Strategy

**Build once, promote the same artifact.** One image per app (`orazaka-router`, `orazaka-worker-*`), built once in CI, then **promoted** dev → staging → prod without rebuild; only the **per-profile config** changes. Infra is **declarative** (Terraform, per-env workspaces). Frontends are deployed as bundles (Next build / Expo).

### 6.2 Pipeline

```mermaid
flowchart LR
    CODE["commit / PR"] --> CI["CI GitHub Actions"]
    CI --> BUILD["build (Maven + npm + python)"]
    BUILD --> TEST["test: unit · IT · hermetic E2E"]
    TEST --> PKG["package: Docker images + front bundles"]
    PKG --> DOCS["docs build + sync → site"]
    PKG --> DDEV["deploy dev"]
    DDEV --> EDEV["hermetic E2E ✓"]
    EDEV --> DSTG["deploy staging (Terraform + images)"]
    DSTG --> ESTG["full E2E ✓"]
    ESTG --> DPRD["deploy prod (same images promoted)"]
    DPRD --> SMK["smoke / synthetic ✓"]
```

### 6.3 What gets deployed

| Component | dev | staging / prod |
| :--- | :--- | :--- |
| `orazaka-router` | native process | Docker image (Terraform) |
| `orazaka-worker-integrations` | native process | Docker image |
| `orazaka-worker-media` | native macOS (MLX) | **cloud GPU** (Modal/RunPod) or GPU container |
| Frontends (web/admin) | dev server | deployed bundle (Next) |
| Infra (PG/Redis/MQ) | docker-compose | managed Terraform |
| Inference (Ollama/SD/SVD) | native Metal | remote GPU endpoint (adapter swap) |

Command: `orazaka deploy --env <dev|staging|prod>` (wraps Terraform + image push + migrations).

> **Open decision**: the cloud provider (router/infra) and the GPU target (Modal vs RunPod vs self-host) remain to be settled. Terraform (`infra/terraform`) and the compute nodes (`infra/compute-nodes`: `modal_app.py`, `runpod.toml`) are already bootstrapped.

---

## 7. `orazaka` CLI reference (single orchestrator)

| Command | Role | Env |
| :--- | :--- | :--- |
| `orazaka install` | detect HW/prereqs, generate `.env` | dev |
| `orazaka start` | Docker infra (PG/pgvector/Redis/RabbitMQ) + check native Ollama | dev |
| `orazaka models pull` | pull the Ollama models | dev |
| `orazaka dev [--only …]` | spawn apps natively, color logs | dev |
| `orazaka onboard` | validate clone-and-run (timeout-guarded) | dev / CI |
| `orazaka test [unit\|it\|e2e]` | run the test tiers | dev / CI |
| `orazaka docs [build\|sync]` | generate + sync docs to the site | CI |
| `orazaka deploy --env <…>` | deploy (Terraform + images + migrations) | CI |

---

## 8. Decision summary (lifecycle)

| # | Topic | Decision |
| :-: | :--- | :--- |
| 1 | Config vs code | one difference per env = the **config**; the code is identical |
| 2 | Inference | native Metal in dev; **adapter swap** to cloud GPU in staging/prod |
| 3 | Infra | `docker-compose` in dev; **Terraform** (workspaces) in staging/prod |
| 4 | Artifacts | **build once, promote**; same images dev→staging→prod |
| 5 | IDE | IntelliJ (backend) + VSCode (front/python), neutral CLI |
| 6 | E2E | hermetic in dev/CI; full in staging; smoke in prod |
| 7 | Docs | generated from code + `orazaka docs sync` → Krizaka site |
| 8 | Orchestration | **the `orazaka` CLI** is the single entry point for the whole cycle |

---

*Written on 2026-06-08 — environments and lifecycle strategy (Claude Code handoff).*
