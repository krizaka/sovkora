# Sovkora CLI

> `apps/cli` of [Sovkora](../../README.md), one repository (pnpm, app layer). Depends on nothing else of Sovkora. Licence: [Apache-2.0](LICENSE).

The developer CLI: install, start, dev, test, docs and pack tooling for the local Sovkora stack, with an offline SQLite queue.

## Quick start

Requirements: git, JDK 21, Node.js 22+, Docker (Python 3.11+ for the media worker).

```bash
npx orazaka install   # no workspace here? clones the platform into ./orazaka, then configures it
cd sovkora && ./mvnw install && pnpm install
npx orazaka start     # Docker infrastructure (PostgreSQL + pgvector, Redis, RabbitMQ)
npx orazaka dev       # the whole stack: services, web (with the operator console), mobile
```

`orazaka` on npm is the short name of the canonical package **`@krizaka/orazaka-cli`** — both run the same CLI:

```bash
npx @krizaka/orazaka-cli install      # the same as npx orazaka install
npm install -g @krizaka/orazaka-cli   # then: orazaka <command>
```

`install` verifies the tools (and offers Homebrew installs on macOS), clones
[krizaka/orazaka](https://github.com/krizaka/orazaka) and every component repository when it is not run inside a
workspace (`--dir <path>`, `--depth <n>`; `--yes` accepts every default without a prompt), sets up the media worker's
Python environment and writes `.env` and `infra/docker-compose.override.yml`. `install --check-only` only reports.
`start`, `dev`, `test` and `docs` run inside a workspace and say how to get one when there is none.

## What it provides

The single orchestrator of the platform (no shell scripts): `orazaka install`, `start` (Docker infra),
`models pull`, `dev`, `onboard`, `test [unit|it|e2e]`, `docs [build|sync]`, `pack list|validate|install|publish`,
`demo seed`, `doctor`.

### Local image models

Images are generated on the Mac by [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) (`sd-server`,
Metal). `orazaka models image` installs the models it can serve — every one under a licence that allows commercial use:

| Model                                           | Licence               |    Disk |    RAM | Measured on an M1 Max (64 GB)           |
| :---------------------------------------------- | :-------------------- | ------: | -----: | :-------------------------------------- |
| `flux-2-klein-4b` — FLUX.2 [klein] 4B (default) | Apache-2.0            |  8.7 GB | ~11 GB | 1024×1024 in ~82 s, 1024×1280 in ~105 s |
| `z-image-turbo` — Z-Image Turbo                 | Apache-2.0            | 11.2 GB | ~12 GB | 1024×1024 in ~233 s                     |
| `v1-5-pruned-emaonly` — Stable Diffusion 1.5    | CreativeML OpenRAIL-M |  4.3 GB |  ~5 GB | 512×512 in ~68 s                        |

```bash
orazaka models image list                    # licence, size, installed, which one is served
orazaka models image pull flux-2-klein-4b    # download into ~/models/stable-diffusion, sha256-verified
orazaka models image use flux-2-klein-4b     # make it the catalogue's default, then restart the stack
```

`sd-server` serves one model at a time: `orazaka start` launches the catalogue's default image model with its own
recipe (steps, guidance, flags from `src/services/image-model-recipes.json`) and refuses to serve another one under its name.
Prose inputs with commas go through a file: `orazaka studio run <installation> --inputs-file inputs.json`.

### Runs as background tasks (ADR-088)

`orazaka runs ls [--watch] [--json]` lists the runs (name, status, current step, elapsed time, cost against the budget; `--watch` refreshes every 3 s); `orazaka runs show <name|id> [--steps] [--json]` shows one run and its folded timeline. Name and cost columns show `-` until the Studio serves them. `orazaka runs stop <ref>` cancels a run, `orazaka runs approve <ref>` approves the step it waits on, `orazaka runs resume <ref> --from <step>` starts a new run from a step (all take `--json`). `orazaka runs send <ref> "<message>"` answers a run that waits for input and `orazaka runs deny <ref> [--reason <text>]` refuses the approval it waits on (the run stops without the step). `orazaka runs wait <ref> [--notify]` follows a run until it ends or needs you. `orazaka studio run <target>` now follows the run to its end; `--background` prints the run id and returns at once, `--notify` sends a desktop notification (Notification Center on macOS, `notify-send` on Linux, the terminal bell elsewhere) when it ends or needs approval.

## Build and test

```bash
pnpm install                      # from the repository root: every app, each with its own lockfile
pnpm --dir apps/cli run validate    # lint, types and tests, as CI runs them
```

CI runs it on every pull request that touches `apps/cli/` (ADR-029: one repository, one CI).
