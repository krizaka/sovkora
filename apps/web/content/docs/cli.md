# CLI Reference

The `orazaka` CLI is the single orchestrator of the platform: it clones and configures the
workspace, starts the infrastructure, runs the whole stack, tests it, builds these docs and
installs packs. No shell script to remember — one command per intent.

## Quick start

Requirements: git, JDK 21, Node.js >=22, Docker (Python 3.11+ for the media worker).

```bash
npx orazaka install   # no workspace here? clones the platform into ./orazaka, then configures it
cd orazaka && ./mvnw install && (cd orazaka-apps/ui && npm install)
npx orazaka start     # Docker infrastructure (PostgreSQL + pgvector, Redis, RabbitMQ)
npx orazaka dev       # the whole stack: services, web, admin, mobile
```

```cli-replay
npx orazaka install --yes
npx orazaka start
npx orazaka dev
```

`orazaka` on npm is the short name of the canonical package `@krizaka/orazaka-cli` — both run the same CLI:

```bash
npx @krizaka/orazaka-cli install      # the same as npx orazaka install
npm install -g @krizaka/orazaka-cli   # then: orazaka <command>
```

`install --check-only` only reports what is missing. `start`, `dev`, `test` and `docs` run inside
a workspace and say how to get one when there is none.

## What happens

What each command of the quick start does, in order — the phases it announces, the processes it launches:

```cli-journey
orazaka install — Setup wizard — verify tools, install dependencies, and configure deployment topology
  1. Tool Verification
  2. Install Missing Tools via Homebrew
  · Workspace (no Orazaka workspace here)
  3. Media Worker Python Environment
  4. Deployment Topology Configuration
orazaka start — Start Orazaka infrastructure — dev mode (Docker middleware + AI engines) or full mode (also Router + UI)
  1. Docker Services (Postgres, Redis, RabbitMQ)
  2. AI Engine (Ollama)
  3. Image Generation (LocalAI)
  3b. Image Generation (stable-diffusion.cpp)
  4. Media Generation Worker
  5a. Starting Router (Spring Boot) (--mode full)
  5b. Starting Next.js UI (--mode full)
orazaka dev — Launch the full Orazaka application stack in parallel (9 services + Web, Mobile)
  EDGE :8088
  ROUTER :8080
  IDENTITY :8083
  AUTOMATION :8082
  KNOWLEDGE :8084
  JOB :8090
  BILLING :8095
  STUDIO :8096
  NOTIFICATIONS :8097
  WEB-CLIENT :3000
  MOBILE :8081
  worker.yaml (Python workers, no HTTP port)
```

## Local dev workflow

The dev loop has two layers — start them in order:

1. **Infrastructure** — `orazaka start --mode dev` brings up the Docker middleware
   (PostgreSQL+pgvector, Redis, RabbitMQ) and the native AI engines (Ollama, LocalAI,
   video worker). Inference runs natively on macOS Metal — never in Docker.
2. **Applications** — `orazaka dev` spawns the services (edge, conversation router, identity,
   automation, knowledge, job, billing, studio, notifications), the Web client, the Web admin
   and the Expo mobile server in parallel. Prefer to debug the conversation router in your IDE?
   Run `ConversationServiceApplication` there and start the rest with `orazaka dev --skip-router`.

`orazaka start --mode full` additionally runs the router and the UI for a
hands-off boot. Tear everything down with `orazaka stop`. On a local stack,
`orazaka demo seed` creates the demo persona with its plan, packs and Studios.

> ℹ️ `start` manages _infrastructure_; `dev` manages _application processes_. They are
> complementary, not alternatives.

## Commands

```cli-commands
33 commands, 39 subcommands — every one in the table below.
```

| Command                                      | Description                                                                                                         |
| :------------------------------------------- | :------------------------------------------------------------------------------------------------------------------ |
| `orazaka agent`                              | Manage Orazaka local executing agent                                                                                |
| `orazaka agent listen`                       | Start CLI agent listener and reverse tunnel                                                                         |
| `orazaka chat [prompt...]`                   | Execute interactive REPL or single-shot chat                                                                        |
| `orazaka cinema`                             | A cinematic short (30–60 s) from a brief: script, keyframes, shots, cut, sound — each an ordinary Studio run        |
| `orazaka cinema new <dir>`                   | Start a production from a brief                                                                                     |
| `orazaka cinema estimate <dir>`              | What the rest of the production will cost and take, before it runs                                                  |
| `orazaka cinema run <dir>`                   | Run (or resume) the production: script, references, keyframes, shots, cut, voice, sound                             |
| `orazaka cinema fetch <dir>`                 | Download the references, keyframes, shots and the film into the production's folder                                 |
| `orazaka cinema reshoot <dir> <shots>`       | Make shots again (a new seed): their video, then the cut and the sound                                              |
| `orazaka cinema status <dir>`                | Where the production is                                                                                             |
| `orazaka cinema plan <dir>`                  | Start a production from a treatment (a directed shot list): no language model, every shot as written                |
| `orazaka cinema sheet <dir>`                 | Make the reference sheet of each character that has a reference render (kept as drawn, never redrawn)               |
| `orazaka cinema jobs <dir>`                  | The pending shots as jobs for a GPU host that runs the worker's own generator                                       |
| `orazaka cinema take <dir> <shot> <file>`    | Record a take a GPU host made for a shot                                                                            |
| `orazaka cinema review <dir> <shot> <file>`  | A take's review sheet: a frame every 8 under the cast's reference sheet                                             |
| `orazaka cinema treat <dir>`                 | Write the story of a brief (arc, intentions, purposeful shots, directed lines, music cues, sound list) and judge it |
| `orazaka cinema critique <treatment>`        | Judge a treatment against the rubric (arc, purpose, alive, on-topic, continuity, voice, music, sound)               |
| `orazaka cinema timeline <treatment>`        | Place every line, music cue and sound of a treatment on the cut, and say which lines do not fit their shots         |
| `orazaka cinema stage <treatment>`           | Check a v3 treatment's staging, camera and cut, and write the questions every keyframe and take must answer         |
| `orazaka config`                             | Interactive configuration wizard — review and update .env variables                                                 |
| `orazaka db`                                 | Database maintenance operations                                                                                     |
| `orazaka db reseed`                          | Drop & re-apply infra/initdb/*.sql (schema + seed). Destroys all local dev data.                                    |
| `orazaka demo`                               | Demonstration persona on the local stack (never on a deployed one)                                                  |
| `orazaka demo seed`                          | Create or refresh Eric, the demo persona: account, onboarding, plan, packs and Studios (replayable)                 |
| `orazaka dev`                                | Launch the full Orazaka application stack in parallel (9 services + Web, Mobile)                                    |
| `orazaka docs`                               | Code-driven documentation: build (generate) \| sync (to the site)                                                   |
| `orazaka docs build`                         | Regenerate docs/_generated/ from the code                                                                           |
| `orazaka docs sync`                          | Copy docs/ (+ generated) into the Krizaka site content tree                                                         |
| `orazaka doctor`                             | Advanced system health check with automatic recovery suggestions                                                    |
| `orazaka forgot [email]`                     | Request a password reset token via email                                                                            |
| `orazaka generate` (alias `gen`, `scaffold`) | Generate features, interceptors, connectors, and configurations                                                     |
| `orazaka graph`                              | Display Operation Graph capability matrix                                                                           |
| `orazaka init`                               | Initialize workspace — detects existing projects, validates config, and fixes gaps                                  |
| `orazaka install`                            | Setup wizard — verify tools, install dependencies, and configure deployment topology                                |
| `orazaka login [email] [password]`           | Authenticate and cache JWT token                                                                                    |
| `orazaka logs`                               | Stream and browse service logs                                                                                      |
| `orazaka mcp`                                | Manage your private dynamic MCP server connections                                                                  |
| `orazaka mcp list`                           | List your registered private MCP servers                                                                            |
| `orazaka mcp register`                       | Register a new private MCP server                                                                                   |
| `orazaka mcp delete <id>`                    | Delete a private MCP server by ID                                                                                   |
| `orazaka models`                             | Scan the host hardware and report which catalog models it can run                                                   |
| `orazaka models image`                       | Local image models (stable-diffusion.cpp, Metal): list, download, choose the one served                             |
| `orazaka models list`                        | List the installable image models, their licence, size and whether they are installed                               |
| `orazaka models pull <id>`                   | Download an image model into ~/models/stable-diffusion and verify its checksums                                     |
| `orazaka models use <id>`                    | Make an installed image model the catalogue's default — the one `orazaka start` serves                              |
| `orazaka onboard`                            | Full clone-and-run onboarding: verify prerequisites, compile, smoke-test M2M handshake, and probe local AI runtimes |
| `orazaka pack`                               | Validate, install and publish pack bundles                                                                          |
| `orazaka pack validate <directory>`          | Check a bundle's manifest, and whether this platform can run it                                                     |
| `orazaka pack install <directory>`           | Install a bundle: capabilities, billing and catalogue, or none of them                                              |
| `orazaka pack publish <directory>`           | Put an installed bundle's pack and Studios on the shelf                                                             |
| `orazaka pack list [directory]`              | List the bundles this deployment's pack sources offer                                                               |
| `orazaka profile`                            | Display authenticated user profile information                                                                      |
| `orazaka recover`                            | Diagnose and recover from common issues                                                                             |
| `orazaka register [username] [email]`        | Register a new user account                                                                                         |
| `orazaka release`                            | Release bundles — check a release manifest and an offline bundle before installing it                               |
| `orazaka release verify <manifest>`          | Check a release manifest, and with --dir the artefacts of an offline bundle against it                              |
| `orazaka reset [token]`                      | Reset account password using a valid reset token                                                                    |
| `orazaka settings`                           | Manage user settings and preferences                                                                                |
| `orazaka settings get`                       | Get all settings and preferences                                                                                    |
| `orazaka settings set <key> <value>`         | Set a user preference key-value pair                                                                                |
| `orazaka start`                              | Start Orazaka infrastructure — dev mode (Docker middleware + AI engines) or full mode (also Router + UI)            |
| `orazaka status`                             | Show live status of all Orazaka services                                                                            |
| `orazaka stop`                               | Teardown the Orazaka infrastructure — containers, workers, and state                                                |
| `orazaka studio`                             | Browse, install and run installable business workflows                                                              |
| `orazaka studio list`                        | Browse the Studio catalogue                                                                                         |
| `orazaka studio installed`                   | List the Studios you have installed                                                                                 |
| `orazaka studio install <studioKey>`         | Install a Studio into your workspace                                                                                |
| `orazaka studio run <target>`                | Run an installed Studio — or, with --studio, a toolkit Studio by its key                                            |
| `orazaka studio logs <runId>`                | Show a run's per-step state and results                                                                             |
| `orazaka test [mode]`                        | Run the test pyramid: unit \| it (Testcontainers) \| e2e (hermetic)                                                 |
| `orazaka verify [token]`                     | Verify account registration token                                                                                   |
| `orazaka video <prompt>`                     | Generate video from a text prompt                                                                                   |

## Arguments and options

### `orazaka chat [prompt...]`

Execute interactive REPL or single-shot chat

| Argument / option     | Description                                |
| :-------------------- | :----------------------------------------- |
| `[prompt...]`         | Optional prompt words for single-shot chat |
| `-t, --text`          | Force text chat mode                       |
| `-i, --image <path>`  | Analyze a poster image                     |
| `-a, --audio <path>`  | Analyze an audio clip                      |
| `-g, --gen-image`     | Generate an image                          |
| `-s, --speech`        | Convert text to speech                     |
| `-m, --model <model>` | Specify AI model name                      |
| `-v, --voice <voice>` | Specify voice name                         |
| `-o, --save <path>`   | Specific file path to save output media    |

### `orazaka cinema new <dir>`

Start a production from a brief

| Argument / option          | Description                                                                       |
| :------------------------- | :-------------------------------------------------------------------------------- |
| `<dir>`                    | The production's folder                                                           |
| `-a, --aspect <aspect>`    | 9:16 or 16:9 (default: `9:16`)                                                    |
| `-d, --duration <seconds>` | 30 to 60 (default: `45`)                                                          |
| `-n, --shots <count>`      | 8 to 12 (default: `10`)                                                           |
| `-l, --language <lang>`    | en or fr (default: `en`)                                                          |
| `--seed <seed>`            | The production's seed (every shot's seed derives from it)                         |
| `--voice <voice>`          | A voice of the speech catalogue for the voice-over                                |
| `--voice-model <model>`    | The speech model of the voice-over (e.g. kokoro-82m while a cinema GPU host runs) |
| `--script-model <model>`   | The language model that writes the script (default: the catalogue's)              |

### `orazaka cinema estimate <dir>`

What the rest of the production will cost and take, before it runs

| Argument / option | Description             |
| :---------------- | :---------------------- |
| `<dir>`           | The production's folder |

### `orazaka cinema run <dir>`

Run (or resume) the production: script, references, keyframes, shots, cut, voice, sound

| Argument / option | Description                                               |
| :---------------- | :-------------------------------------------------------- |
| `<dir>`           | The production's folder                                   |
| `-y, --yes`       | Do not ask before spending                                |
| `--max-runs <n>`  | Stop after this many runs (review in between)             |
| `--model <name>`  | The video model of the shots (default: `wan2.2-i2v-a14b`) |

### `orazaka cinema fetch <dir>`

Download the references, keyframes, shots and the film into the production's folder

| Argument / option | Description             |
| :---------------- | :---------------------- |
| `<dir>`           | The production's folder |

### `orazaka cinema reshoot <dir> <shots>`

Make shots again (a new seed): their video, then the cut and the sound

| Argument / option | Description                   |
| :---------------- | :---------------------------- |
| `<dir>`           | The production's folder       |
| `<shots>`         | Shot numbers, comma-separated |
| `--new-keyframe`  | Make the keyframe again too   |

### `orazaka cinema status <dir>`

Where the production is

| Argument / option | Description             |
| :---------------- | :---------------------- |
| `<dir>`           | The production's folder |

### `orazaka cinema plan <dir>`

Start a production from a treatment (a directed shot list): no language model, every shot as written

| Argument / option        | Description                                                           |
| :----------------------- | :-------------------------------------------------------------------- |
| `<dir>`                  | The production's folder                                               |
| `-r, --references <dir>` | Where the characters' reference renders are (<name>-alpha.png)        |
| `-l, --language <lang>`  | en or fr (the captions) (default: `en`)                               |
| `--seed <seed>`          | Overrides the treatment's seedBase                                    |
| `--update`               | The treatment changed: keep every shot already made, add the new ones |

### `orazaka cinema sheet <dir>`

Make the reference sheet of each character that has a reference render (kept as drawn, never redrawn)

| Argument / option | Description             |
| :---------------- | :---------------------- |
| `<dir>`           | The production's folder |

### `orazaka cinema jobs <dir>`

The pending shots as jobs for a GPU host that runs the worker's own generator

| Argument / option  | Description                                   |
| :----------------- | :-------------------------------------------- |
| `<dir>`            | The production's folder                       |
| `-o, --out <file>` | Write them to a file instead of printing them |

### `orazaka cinema take <dir> <shot> <file>`

Record a take a GPU host made for a shot

| Argument / option | Description                       |
| :---------------- | :-------------------------------- |
| `<dir>`           | The production's folder           |
| `<shot>`          | The shot's number                 |
| `<file>`          | The take (mp4)                    |
| `--seconds <s>`   | How long the host took to make it |

### `orazaka cinema review <dir> <shot> <file>`

A take's review sheet: a frame every 8 under the cast's reference sheet

| Argument / option | Description             |
| :---------------- | :---------------------- |
| `<dir>`           | The production's folder |
| `<shot>`          | The shot's number       |
| `<file>`          | The take (mp4)          |

### `orazaka cinema treat <dir>`

Write the story of a brief (arc, intentions, purposeful shots, directed lines, music cues, sound list) and judge it

| Argument / option       | Description                                                            |
| :---------------------- | :--------------------------------------------------------------------- |
| `<dir>`                 | Where the treatment and its critique are written                       |
| `-l, --language <lang>` | en or fr (default: `en`)                                               |
| `-d, --duration <s>`    | Seconds of film (default: `60`)                                        |
| `-a, --aspect <aspect>` | 16:9 or 9:16 (default: `16:9`)                                         |
| `--revisions <n>`       | How many times a rejected draft goes back to the writer (default: `2`) |
| `--no-judge`            | The rubric alone decides (no language-model judge)                     |
| `--model <name>`        | The chat model of the writer and the judge                             |

### `orazaka cinema critique <treatment>`

Judge a treatment against the rubric (arc, purpose, alive, on-topic, continuity, voice, music, sound)

| Argument / option       | Description                                                  |
| :---------------------- | :----------------------------------------------------------- |
| `<treatment>`           | The treatment (JSON)                                         |
| `-l, --language <lang>` | en or fr (default: `en`)                                     |
| `--judge`               | Also ask the language-model judge (a cinematic-critique run) |
| `--model <name>`        | The judge's chat model                                       |
| `-o, --out <file>`      | Write the critique (JSON) there                              |

### `orazaka cinema timeline <treatment>`

Place every line, music cue and sound of a treatment on the cut, and say which lines do not fit their shots

| Argument / option       | Description                                                                                          |
| :---------------------- | :--------------------------------------------------------------------------------------------------- |
| `<treatment>`           | The treatment (JSON)                                                                                 |
| `-l, --language <lang>` | en or fr (default: `en`)                                                                             |
| `--voice <report>`      | The voice step's report: [{id, seconds}] measured per line                                           |
| `--spans <file>`        | The cut's own shot spans ([[start, end], …], e.g. the worker's cut.timeline) instead of back to back |

### `orazaka cinema stage <treatment>`

Check a v3 treatment's staging, camera and cut, and write the questions every keyframe and take must answer

| Argument / option  | Description                                                                            |
| :----------------- | :------------------------------------------------------------------------------------- |
| `<treatment>`      | The treatment (JSON, with score.bpm, beats, camera, angle, light and staging per shot) |
| `-o, --out <file>` | Write the frame questions (JSON: per shot, then the take questions) there              |

### `orazaka config`

Interactive configuration wizard — review and update .env variables

| Argument / option       | Description                           |
| :---------------------- | :------------------------------------ |
| `-l, --list`            | List all current configuration values |
| `-c, --category <name>` | Jump directly to a specific category  |

### `orazaka db reseed`

Drop & re-apply infra/initdb/*.sql (schema + seed). Destroys all local dev data.

| Argument / option | Description                                     |
| :---------------- | :---------------------------------------------- |
| `-y, --yes`       | Skip the destructive-action confirmation prompt |

### `orazaka demo seed`

Create or refresh Eric, the demo persona: account, onboarding, plan, packs and Studios (replayable)

| Argument / option | Description                                                                                                                 |
| :---------------- | :-------------------------------------------------------------------------------------------------------------------------- |
| `--fresh`         | Delete Eric's conversations first, for a demo that starts from a clean history                                              |
| `--runs`          | Also run Eric's demo work on this stack: a product photo, a voice-over, a prospect dossier, a lease check, an invoice check |
| `--with-video`    | With --runs, also generate a few seconds of video (loads a video model: one heavy job, never by default)                    |

### `orazaka dev`

Launch the full Orazaka application stack in parallel (9 services + Web, Mobile)

| Argument / option      | Description                                                                                                                         |
| :--------------------- | :---------------------------------------------------------------------------------------------------------------------------------- |
| `--only <services>`    | Launch only these services (comma-separated: edge,router,identity,automation,knowledge,job,billing,studio,notifications,web,mobile) |
| `--skip-edge`          | Skip the Edge gateway (strangler facade on :8088)                                                                                   |
| `--skip-identity`      | Skip the Identity service (:8083)                                                                                                   |
| `--skip-router`        | Skip the Router (Spring Boot) backend — run RouterApplication from your IDE instead                                                 |
| `--skip-core`          | Deprecated alias for --skip-router                                                                                                  |
| `--skip-automation`    | Skip the Automation service (AMQP on :8082)                                                                                         |
| `--skip-knowledge`     | Skip the Knowledge service (:8084)                                                                                                  |
| `--skip-job`           | Skip the Job Orchestration service (:8090)                                                                                          |
| `--skip-billing`       | Skip the Billing service (:8095)                                                                                                    |
| `--skip-studio`        | Skip the Studio service (:8096)                                                                                                     |
| `--skip-notifications` | Skip the Notification service (:8097)                                                                                               |
| `--skip-admin`         | Deprecated no-op: the operator console is served by the web client                                                                  |
| `--skip-mobile`        | Skip Expo mobile server                                                                                                             |
| `--skip-web`           | Skip Web client                                                                                                                     |
| `--skip-workers`       | Skip the Python workers declared by worker.yaml                                                                                     |

### `orazaka docs build`

Regenerate docs/_generated/ from the code

| Argument / option | Description                                                      |
| :---------------- | :--------------------------------------------------------------- |
| `--check`         | Fail if any generated doc is stale (build gate), without writing |

### `orazaka doctor`

Advanced system health check with automatic recovery suggestions

| Argument / option | Description                               |
| :---------------- | :---------------------------------------- |
| `-q, --quiet`     | Only show issues, suppress passing checks |
| `--fix`           | Attempt to auto-fix detected issues       |
| `-v, --verbose`   | Show detailed diagnostic output           |

### `orazaka forgot [email]`

Request a password reset token via email

| Argument / option | Description           |
| :---------------- | :-------------------- |
| `[email]`         | Account email address |

### `orazaka init`

Initialize workspace — detects existing projects, validates config, and fixes gaps

| Argument / option   | Description                                   |
| :------------------ | :-------------------------------------------- |
| `-f, --force`       | Overwrite existing configuration              |
| `-y, --yes`         | Non-interactive mode with defaults            |
| `--skip-validation` | Skip diagnostic checks                        |
| `--dir <path>`      | Target directory (default: current directory) |

### `orazaka install`

Setup wizard — verify tools, install dependencies, and configure deployment topology

| Argument / option | Description                                                                                   |
| :---------------- | :-------------------------------------------------------------------------------------------- |
| `--check-only`    | Read-only: verify tool availability & versions, then exit (no .env / compose changes)         |
| `-y, --yes`       | Non-interactive mode — accept every default (clone, tools, local dev topology, bundled infra) |
| `--dir <path>`    | Where to clone the platform when no workspace is found (default: `orazaka`)                   |
| `--depth <n>`     | git clone depth for the platform repositories (0 = full history) (default: `1`)               |

### `orazaka login [email] [password]`

Authenticate and cache JWT token

| Argument / option | Description           |
| :---------------- | :-------------------- |
| `[email]`         | Account email address |
| `[password]`      | Account password      |

### `orazaka logs`

Stream and browse service logs

| Argument / option    | Description                                                              |
| :------------------- | :----------------------------------------------------------------------- |
| `--service <name>`   | Tail a specific service (router, ollama, media-worker, image-worker, ui) |
| `--list`             | List all available log files with sizes                                  |
| `--since <duration>` | Show logs from last N hours (e.g., 1h, 30m) (default: `24h`)             |
| `--tail <lines>`     | Number of lines to show from end of file (default: `50`)                 |

### `orazaka mcp register`

Register a new private MCP server

| Argument / option      | Description                         |
| :--------------------- | :---------------------------------- |
| `--auth-token <token>` | Optional authorization token/header |

### `orazaka models pull <id>`

Download an image model into ~/models/stable-diffusion and verify its checksums

| Argument / option | Description                                |
| :---------------- | :----------------------------------------- |
| `<id>`            | Model id (see `orazaka models image list`) |

### `orazaka models use <id>`

Make an installed image model the catalogue's default — the one `orazaka start` serves

| Argument / option | Description                                |
| :---------------- | :----------------------------------------- |
| `<id>`            | Model id (see `orazaka models image list`) |

### `orazaka pack validate <directory>`

Check a bundle's manifest, and whether this platform can run it

| Argument / option | Description                                                  |
| :---------------- | :----------------------------------------------------------- |
| `<directory>`     | The bundle directory holding pack.yaml                       |
| `--offline`       | Check the manifest's shape only, without asking the platform |

### `orazaka pack install <directory>`

Install a bundle: capabilities, billing and catalogue, or none of them

| Argument / option | Description                                                                  |
| :---------------- | :--------------------------------------------------------------------------- |
| `<directory>`     | The bundle directory holding pack.yaml, or a directory of bundles with --all |
| `--all`           | Install every bundle in the directory — what a fresh database needs          |

### `orazaka pack publish <directory>`

Put an installed bundle's pack and Studios on the shelf

| Argument / option | Description                            |
| :---------------- | :------------------------------------- |
| `<directory>`     | The bundle directory holding pack.yaml |

### `orazaka pack list [directory]`

List the bundles this deployment's pack sources offer

| Argument / option | Description                                              |
| :---------------- | :------------------------------------------------------- |
| `[directory]`     | Look in this directory instead of the configured sources |

### `orazaka register [username] [email]`

Register a new user account

| Argument / option       | Description                       |
| :---------------------- | :-------------------------------- |
| `[username]`            | Desired username                  |
| `[email]`               | Account email address             |
| `-l, --language <lang>` | BCP 47 language tag (e.g. en, ja) |

### `orazaka release verify <manifest>`

Check a release manifest, and with --dir the artefacts of an offline bundle against it

| Argument / option   | Description                                                             |
| :------------------ | :---------------------------------------------------------------------- |
| `<manifest>`        | Path to the release manifest (orazaka-release.json)                     |
| `--dir <path>`      | Offline bundle directory whose artefacts are checked (size and SHA-256) |
| `--target <target>` | Only the artefacts of one target: mac-arm64, linux-amd64 or linux-arm64 |
| `--json`            | Print the result as JSON                                                |

### `orazaka reset [token]`

Reset account password using a valid reset token

| Argument / option | Description          |
| :---------------- | :------------------- |
| `[token]`         | Password reset token |

### `orazaka settings set <key> <value>`

Set a user preference key-value pair

| Argument / option | Description             |
| :---------------- | :---------------------- |
| `<key>`           | Preference key to set   |
| `<value>`         | Preference value to set |

### `orazaka start`

Start Orazaka infrastructure — dev mode (Docker middleware + AI engines) or full mode (also Router + UI)

| Argument / option     | Description                                                                                         |
| :-------------------- | :-------------------------------------------------------------------------------------------------- |
| `--mode <mode>`       | Startup mode: dev (default, infra + AI engines) or full (also Router + Next.js UI) (default: `dev`) |
| `--only <service>`    | Start only a specific service (postgres, redis, rabbitmq, ollama, localai, image-gen, media-worker) |
| `--skip-health`       | Skip final health verification                                                                      |
| `--wait-timeout <ms>` | Timeout for service startup (default: 60000ms) (default: `60000`)                                   |
| `--allow-partial`     | Allow partial startup (some services may fail)                                                      |
| `--verbose`           | Show detailed startup logs                                                                          |
| `--logs`              | Tail log files after startup                                                                        |

### `orazaka status`

Show live status of all Orazaka services

| Argument / option | Description             |
| :---------------- | :---------------------- |
| `--json`          | Output status as JSON   |
| `--watch`         | Refresh every 5 seconds |

### `orazaka stop`

Teardown the Orazaka infrastructure — containers, workers, and state

| Argument / option | Description                                     |
| :---------------- | :---------------------------------------------- |
| `--purge`         | Also purge database data and upload directories |
| `-y, --yes`       | Skip confirmation prompts                       |

### `orazaka studio list`

Browse the Studio catalogue

| Argument / option          | Description     |
| :------------------------- | :-------------- |
| `-p, --profession <trade>` | Filter by trade |

### `orazaka studio install <studioKey>`

Install a Studio into your workspace

| Argument / option      | Description                            |
| :--------------------- | :------------------------------------- |
| `<studioKey>`          | The Studio to install                  |
| `-c, --config <pairs>` | Configuration as key=value,key2=value2 |

### `orazaka studio run <target>`

Run an installed Studio — or, with --studio, a toolkit Studio by its key

| Argument / option          | Description                                                                             |
| :------------------------- | :-------------------------------------------------------------------------------------- |
| `<target>`                 | The installation to run (or the Studio key with --studio)                               |
| `-i, --inputs <pairs>`     | Inputs as key=value,key2=a\|b\|c (pipes make a list)                                    |
| `-f, --inputs-file <file>` | Inputs as one JSON object (for prose with commas); --inputs pairs apply on top          |
| `-s, --studio`             | The target is a Studio key: a toolkit Studio (media, art direction) has no installation |

### `orazaka studio logs <runId>`

Show a run's per-step state and results

| Argument / option | Description        |
| :---------------- | :----------------- |
| `<runId>`         | The run to inspect |

### `orazaka verify [token]`

Verify account registration token

| Argument / option | Description              |
| :---------------- | :----------------------- |
| `[token]`         | Email verification token |

### `orazaka video <prompt>`

Generate video from a text prompt

| Argument / option          | Description                                                               |
| :------------------------- | :------------------------------------------------------------------------ |
| `<prompt>`                 | Text prompt describing the desired video                                  |
| `-d, --duration <seconds>` | Duration of the video in seconds (default: `4`)                           |
| `-m, --model <model>`      | Specify AI model name                                                     |
| `-a, --aspect <ratio>`     | Frame: 9:16 (default of the Studio), 16:9 or 1:1 — the model must make it |
| `-s, --seed <number>`      | The same prompt, model and seed make the same shot again                  |
| `-o, --output <path>`      | Specific file path to save the video                                      |
