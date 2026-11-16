---
title: CLI Reference
description: sovkora CLI commands, subcommands, arguments and options, extracted from the command definitions.
category: DevEx
order: 7
generated: true
---

# CLI Reference

The `sovkora` CLI is the single orchestrator of the platform: it clones and configures the
workspace, starts the infrastructure, runs the whole stack, tests it, builds these docs and
installs packs. No shell script to remember — one command per intent.

## Quick start

Requirements: git, JDK 21, Node.js >=22, Docker (Python 3.11+ for the media worker).

```bash
npx sovkora install   # no workspace here? clones the platform into ./sovkora, then configures it
cd sovkora && ./mvnw install && pnpm install
npx sovkora start     # Docker infrastructure (PostgreSQL + pgvector, Redis, RabbitMQ)
npx sovkora dev       # the whole stack: services, web, admin, mobile
```

```cli-replay
npx sovkora install --yes
npx sovkora start
npx sovkora dev
```

The package is `@krizaka/sovkora-cli`; installed, the command is `sovkora`:

```bash
npm install -g @krizaka/sovkora-cli   # then: sovkora <command>
```

`install --check-only` only reports what is missing. `start`, `dev`, `test` and `docs` run inside
a workspace and say how to get one when there is none.

## What happens

What each command of the quick start does, in order — the phases it announces, the processes it launches:

```cli-journey
sovkora install — Setup wizard — verify tools, install dependencies, and configure deployment topology
  1. Tool Verification
  2. Install Missing Tools via Homebrew
  · Workspace (no Sovkora workspace here)
  3. Media Worker Python Environment
  4. Deployment Topology Configuration
sovkora start — Start Sovkora infrastructure — dev mode (Docker middleware + AI engines) or full mode (also Router + UI)
  1. Docker Services (Postgres, Redis, RabbitMQ)
  2. AI Engine (Ollama)
  3. Image Generation (LocalAI)
  3b. Image Generation (stable-diffusion.cpp)
  4. Media Generation Worker
  5a. Starting Router (Spring Boot) (--mode full)
  5b. Starting Next.js UI (--mode full)
sovkora dev — Launch the full Sovkora application stack in parallel (9 services + Web, Mobile)
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

1. **Infrastructure** — `sovkora start --mode dev` brings up the Docker middleware
   (PostgreSQL+pgvector, Redis, RabbitMQ) and the native AI engines (Ollama, LocalAI,
   video worker). Inference runs natively on macOS Metal — never in Docker.
2. **Applications** — `sovkora dev` spawns the services (edge, conversation router, identity,
   automation, knowledge, job, billing, studio, notifications), the Web client, the Web admin
   and the Expo mobile server in parallel. Prefer to debug the conversation router in your IDE?
   Run `ConversationServiceApplication` there and start the rest with `sovkora dev --skip-router`.

`sovkora start --mode full` additionally runs the router and the UI for a
hands-off boot. Tear everything down with `sovkora stop`. On a local stack,
`sovkora demo seed` creates the demo persona with its plan, packs and Studios.

> ℹ️ `start` manages *infrastructure*; `dev` manages *application processes*. They are
> complementary, not alternatives.

## Commands

```cli-commands
34 commands, 45 subcommands — every one in the table below.
```

| Command | Description |
|:---|:---|
| `sovkora agent` | Manage Sovkora local executing agent |
| `sovkora agent listen` | Start CLI agent listener and reverse tunnel |
| `sovkora chat [prompt...]` | Execute interactive REPL or single-shot chat |
| `sovkora cinema` | A cinematic short (30–60 s) from a brief: script, keyframes, shots, cut, sound — each an ordinary Studio run |
| `sovkora cinema new <dir>` | Start a production from a brief |
| `sovkora cinema estimate <dir>` | What the rest of the production will cost and take, before it runs |
| `sovkora cinema run <dir>` | Run (or resume) the production: script, references, keyframes, shots, cut, voice, sound |
| `sovkora cinema fetch <dir>` | Download the references, keyframes, shots and the film into the production's folder |
| `sovkora cinema reshoot <dir> <shots>` | Make shots again (a new seed): their video, then the cut and the sound |
| `sovkora cinema status <dir>` | Where the production is |
| `sovkora cinema plan <dir>` | Start a production from a treatment (a directed shot list): no language model, every shot as written |
| `sovkora cinema sheet <dir>` | Make the reference sheet of each character that has a reference render (kept as drawn, never redrawn) |
| `sovkora cinema jobs <dir>` | The pending shots as jobs for a GPU host that runs the worker's own generator |
| `sovkora cinema take <dir> <shot> <file>` | Record a take a GPU host made for a shot |
| `sovkora cinema review <dir> <shot> <file>` | A take's review sheet: a frame every 8 under the cast's reference sheet |
| `sovkora cinema treat <dir>` | Write the story of a brief (arc, intentions, purposeful shots, directed lines, music cues, sound list) and judge it |
| `sovkora cinema critique <treatment>` | Judge a treatment against the rubric (arc, purpose, alive, on-topic, continuity, voice, music, sound) |
| `sovkora cinema timeline <treatment>` | Place every line, music cue and sound of a treatment on the cut, and say which lines do not fit their shots |
| `sovkora cinema stage <treatment>` | Check a v3 treatment's staging, camera and cut, and write the questions every keyframe and take must answer |
| `sovkora config` | Interactive configuration wizard — review and update .env variables |
| `sovkora db` | Database maintenance operations |
| `sovkora db reseed` | Drop & re-apply infra/initdb/*.sql (schema + seed). Destroys all local dev data. |
| `sovkora demo` | Demonstration persona on the local stack (never on a deployed one) |
| `sovkora demo seed` | Create or refresh Eric, the demo persona: account, onboarding, plan, packs and Studios (replayable) |
| `sovkora dev` | Launch the full Sovkora application stack in parallel (9 services + Web, Mobile) |
| `sovkora docs` | Code-driven documentation: build (generate) \| sync (to the site) |
| `sovkora docs build` | Regenerate docs/_generated/ from the code |
| `sovkora docs sync` | Copy docs/ (+ generated) into the Krizaka site content tree |
| `sovkora doctor` | Advanced system health check with automatic recovery suggestions |
| `sovkora forgot [email]` | Request a password reset token via email |
| `sovkora generate` (alias `gen`, `scaffold`) | Generate features, interceptors, connectors, and configurations |
| `sovkora graph` | Display Operation Graph capability matrix |
| `sovkora init` | Initialize workspace — detects existing projects, validates config, and fixes gaps |
| `sovkora install` | Setup wizard — verify tools, install dependencies, and configure deployment topology |
| `sovkora login [email] [password]` | Authenticate and cache JWT token |
| `sovkora logs` | Stream and browse service logs |
| `sovkora mcp` | Manage your private dynamic MCP server connections |
| `sovkora mcp list` | List your registered private MCP servers |
| `sovkora mcp register` | Register a new private MCP server |
| `sovkora mcp delete <id>` | Delete a private MCP server by ID |
| `sovkora models` | Scan the host hardware and report which catalog models it can run |
| `sovkora models image` | Local image models (stable-diffusion.cpp, Metal): list, download, choose the one served |
| `sovkora models list` | List the installable image models, their licence, size and whether they are installed |
| `sovkora models pull <id>` | Download an image model into ~/models/stable-diffusion and verify its checksums |
| `sovkora models use <id>` | Make an installed image model the catalogue's default — the one `sovkora start` serves |
| `sovkora onboard` | Full clone-and-run onboarding: verify prerequisites, compile, smoke-test M2M handshake, and probe local AI runtimes |
| `sovkora pack` | Validate, install and publish pack bundles |
| `sovkora pack validate <directory>` | Check a bundle's manifest, and whether this platform can run it |
| `sovkora pack install <directory>` | Install a bundle: capabilities, billing and catalogue, or none of them |
| `sovkora pack publish <directory>` | Put an installed bundle's pack and Studios on the shelf |
| `sovkora pack list [directory]` | List the bundles this deployment's pack sources offer |
| `sovkora profile` | Display authenticated user profile information |
| `sovkora recover` | Diagnose and recover from common issues |
| `sovkora register [username] [email]` | Register a new user account |
| `sovkora release` | Release bundles — check a release manifest and an offline bundle before installing it |
| `sovkora release verify <manifest>` | Check a release manifest, and with --dir the artefacts of an offline bundle against it |
| `sovkora reset [token]` | Reset account password using a valid reset token |
| `sovkora runs` | Runs as background tasks: list and inspect them by name or id |
| `sovkora runs ls` (alias `list`) | List the runs: name, status, current step, elapsed time, cost against the budget |
| `sovkora runs show <ref> <ref>` | Show one run, by name or id |
| `sovkora runs send <ref> <message>` | Answer a run that is waiting for input: the message becomes the output of the step it waits on |
| `sovkora runs deny <ref>` | Refuse the approval a run is waiting on: the run stops without the step and its hold is released |
| `sovkora runs wait <ref>` | Follow a run until it ends or needs your approval |
| `sovkora runs resume <ref>` | Start a new run from a step of a finished one; the steps before it keep their outputs |
| `sovkora settings` | Manage user settings and preferences |
| `sovkora settings get` | Get all settings and preferences |
| `sovkora settings set <key> <value>` | Set a user preference key-value pair |
| `sovkora start` | Start Sovkora infrastructure — dev mode (Docker middleware + AI engines) or full mode (also Router + UI) |
| `sovkora status` | Show live status of all Sovkora services |
| `sovkora stop` | Teardown the Sovkora infrastructure — containers, workers, and state |
| `sovkora studio` | Browse, install and run installable business workflows |
| `sovkora studio list` | Browse the Studio catalogue |
| `sovkora studio installed` | List the Studios you have installed |
| `sovkora studio install <studioKey>` | Install a Studio into your workspace |
| `sovkora studio run <target>` | Run an installed Studio — or, with --studio, a toolkit Studio by its key |
| `sovkora studio logs <runId>` | Show a run's per-step state and results |
| `sovkora test [mode]` | Run the test pyramid: unit \| it (Testcontainers) \| e2e (hermetic) |
| `sovkora verify [token]` | Verify account registration token |
| `sovkora video <prompt>` | Generate video from a text prompt |

## Arguments and options

### `sovkora chat [prompt...]`

Execute interactive REPL or single-shot chat

| Argument / option | Description |
|:---|:---|
| `[prompt...]` | Optional prompt words for single-shot chat |
| `-t, --text` | Force text chat mode |
| `-i, --image <path>` | Analyze a poster image |
| `-a, --audio <path>` | Analyze an audio clip |
| `-g, --gen-image` | Generate an image |
| `-s, --speech` | Convert text to speech |
| `-m, --model <model>` | Specify AI model name |
| `-v, --voice <voice>` | Specify voice name |
| `-o, --save <path>` | Specific file path to save output media |

### `sovkora cinema new <dir>`

Start a production from a brief

| Argument / option | Description |
|:---|:---|
| `<dir>` | The production's folder |
| `-a, --aspect <aspect>` | 9:16 or 16:9 (default: `9:16`) |
| `-d, --duration <seconds>` | 30 to 60 (default: `45`) |
| `-n, --shots <count>` | 8 to 12 (default: `10`) |
| `-l, --language <lang>` | en or fr (default: `en`) |
| `--seed <seed>` | The production's seed (every shot's seed derives from it) |
| `--voice <voice>` | A voice of the speech catalogue for the voice-over |
| `--voice-model <model>` | The speech model of the voice-over (e.g. kokoro-82m while a cinema GPU host runs) |
| `--script-model <model>` | The language model that writes the script (default: the catalogue's) |

### `sovkora cinema estimate <dir>`

What the rest of the production will cost and take, before it runs

| Argument / option | Description |
|:---|:---|
| `<dir>` | The production's folder |

### `sovkora cinema run <dir>`

Run (or resume) the production: script, references, keyframes, shots, cut, voice, sound

| Argument / option | Description |
|:---|:---|
| `<dir>` | The production's folder |
| `-y, --yes` | Do not ask before spending |
| `--max-runs <n>` | Stop after this many runs (review in between) |
| `--model <name>` | The video model of the shots (default: `wan2.2-i2v-a14b`) |

### `sovkora cinema fetch <dir>`

Download the references, keyframes, shots and the film into the production's folder

| Argument / option | Description |
|:---|:---|
| `<dir>` | The production's folder |

### `sovkora cinema reshoot <dir> <shots>`

Make shots again (a new seed): their video, then the cut and the sound

| Argument / option | Description |
|:---|:---|
| `<dir>` | The production's folder |
| `<shots>` | Shot numbers, comma-separated |
| `--new-keyframe` | Make the keyframe again too |

### `sovkora cinema status <dir>`

Where the production is

| Argument / option | Description |
|:---|:---|
| `<dir>` | The production's folder |

### `sovkora cinema plan <dir>`

Start a production from a treatment (a directed shot list): no language model, every shot as written

| Argument / option | Description |
|:---|:---|
| `<dir>` | The production's folder |
| `-r, --references <dir>` | Where the characters' reference renders are (<name>-alpha.png) |
| `-l, --language <lang>` | en or fr (the captions) (default: `en`) |
| `--seed <seed>` | Overrides the treatment's seedBase |
| `--update` | The treatment changed: keep every shot already made, add the new ones |

### `sovkora cinema sheet <dir>`

Make the reference sheet of each character that has a reference render (kept as drawn, never redrawn)

| Argument / option | Description |
|:---|:---|
| `<dir>` | The production's folder |

### `sovkora cinema jobs <dir>`

The pending shots as jobs for a GPU host that runs the worker's own generator

| Argument / option | Description |
|:---|:---|
| `<dir>` | The production's folder |
| `-o, --out <file>` | Write them to a file instead of printing them |

### `sovkora cinema take <dir> <shot> <file>`

Record a take a GPU host made for a shot

| Argument / option | Description |
|:---|:---|
| `<dir>` | The production's folder |
| `<shot>` | The shot's number |
| `<file>` | The take (mp4) |
| `--seconds <s>` | How long the host took to make it |

### `sovkora cinema review <dir> <shot> <file>`

A take's review sheet: a frame every 8 under the cast's reference sheet

| Argument / option | Description |
|:---|:---|
| `<dir>` | The production's folder |
| `<shot>` | The shot's number |
| `<file>` | The take (mp4) |

### `sovkora cinema treat <dir>`

Write the story of a brief (arc, intentions, purposeful shots, directed lines, music cues, sound list) and judge it

| Argument / option | Description |
|:---|:---|
| `<dir>` | Where the treatment and its critique are written |
| `-l, --language <lang>` | en or fr (default: `en`) |
| `-d, --duration <s>` | Seconds of film (default: `60`) |
| `-a, --aspect <aspect>` | 16:9 or 9:16 (default: `16:9`) |
| `--revisions <n>` | How many times a rejected draft goes back to the writer (default: `2`) |
| `--no-judge` | The rubric alone decides (no language-model judge) |
| `--model <name>` | The chat model of the writer and the judge |

### `sovkora cinema critique <treatment>`

Judge a treatment against the rubric (arc, purpose, alive, on-topic, continuity, voice, music, sound)

| Argument / option | Description |
|:---|:---|
| `<treatment>` | The treatment (JSON) |
| `-l, --language <lang>` | en or fr (default: `en`) |
| `--judge` | Also ask the language-model judge (a cinematic-critique run) |
| `--model <name>` | The judge's chat model |
| `-o, --out <file>` | Write the critique (JSON) there |

### `sovkora cinema timeline <treatment>`

Place every line, music cue and sound of a treatment on the cut, and say which lines do not fit their shots

| Argument / option | Description |
|:---|:---|
| `<treatment>` | The treatment (JSON) |
| `-l, --language <lang>` | en or fr (default: `en`) |
| `--voice <report>` | The voice step's report: [{id, seconds}] measured per line |
| `--spans <file>` | The cut's own shot spans ([[start, end], …], e.g. the worker's cut.timeline) instead of back to back |

### `sovkora cinema stage <treatment>`

Check a v3 treatment's staging, camera and cut, and write the questions every keyframe and take must answer

| Argument / option | Description |
|:---|:---|
| `<treatment>` | The treatment (JSON, with score.bpm, beats, camera, angle, light and staging per shot) |
| `-o, --out <file>` | Write the frame questions (JSON: per shot, then the take questions) there |

### `sovkora config`

Interactive configuration wizard — review and update .env variables

| Argument / option | Description |
|:---|:---|
| `-l, --list` | List all current configuration values |
| `-c, --category <name>` | Jump directly to a specific category |

### `sovkora db reseed`

Drop & re-apply infra/initdb/*.sql (schema + seed). Destroys all local dev data.

| Argument / option | Description |
|:---|:---|
| `-y, --yes` | Skip the destructive-action confirmation prompt |

### `sovkora demo seed`

Create or refresh Eric, the demo persona: account, onboarding, plan, packs and Studios (replayable)

| Argument / option | Description |
|:---|:---|
| `--fresh` | Delete Eric's conversations first, for a demo that starts from a clean history |
| `--runs` | Also run Eric's demo work on this stack: a product photo, a voice-over, a prospect dossier, a lease check, an invoice check |
| `--with-video` | With --runs, also generate a few seconds of video (loads a video model: one heavy job, never by default) |

### `sovkora dev`

Launch the full Sovkora application stack in parallel (9 services + Web, Mobile)

| Argument / option | Description |
|:---|:---|
| `--only <services>` | Launch only these services (comma-separated: edge,router,identity,automation,knowledge,job,billing,studio,notifications,web,mobile) |
| `--skip-edge` | Skip the Edge gateway (strangler facade on :8088) |
| `--skip-identity` | Skip the Identity service (:8083) |
| `--skip-router` | Skip the Router (Spring Boot) backend — run RouterApplication from your IDE instead |
| `--skip-core` | Deprecated alias for --skip-router |
| `--skip-automation` | Skip the Automation service (AMQP on :8082) |
| `--skip-knowledge` | Skip the Knowledge service (:8084) |
| `--skip-job` | Skip the Job Orchestration service (:8090) |
| `--skip-billing` | Skip the Billing service (:8095) |
| `--skip-studio` | Skip the Studio service (:8096) |
| `--skip-notifications` | Skip the Notification service (:8097) |
| `--skip-admin` | Deprecated no-op: the operator console is served by the web client |
| `--skip-mobile` | Skip Expo mobile server |
| `--skip-web` | Skip Web client |
| `--skip-workers` | Skip the Python workers declared by worker.yaml |

### `sovkora docs build`

Regenerate docs/_generated/ from the code

| Argument / option | Description |
|:---|:---|
| `--check` | Fail if any generated doc is stale (build gate), without writing |

### `sovkora doctor`

Advanced system health check with automatic recovery suggestions

| Argument / option | Description |
|:---|:---|
| `-q, --quiet` | Only show issues, suppress passing checks |
| `--fix` | Attempt to auto-fix detected issues |
| `-v, --verbose` | Show detailed diagnostic output |

### `sovkora forgot [email]`

Request a password reset token via email

| Argument / option | Description |
|:---|:---|
| `[email]` | Account email address |

### `sovkora init`

Initialize workspace — detects existing projects, validates config, and fixes gaps

| Argument / option | Description |
|:---|:---|
| `-f, --force` | Overwrite existing configuration |
| `-y, --yes` | Non-interactive mode with defaults |
| `--skip-validation` | Skip diagnostic checks |
| `--dir <path>` | Target directory (default: current directory) |

### `sovkora install`

Setup wizard — verify tools, install dependencies, and configure deployment topology

| Argument / option | Description |
|:---|:---|
| `--check-only` | Read-only: verify tool availability & versions, then exit (no .env / compose changes) |
| `-y, --yes` | Non-interactive mode — accept every default (clone, tools, local dev topology, bundled infra) |
| `--dir <path>` | Where to clone the platform when no workspace is found (default: `sovkora`) |
| `--depth <n>` | git clone depth for the platform repositories (0 = full history) (default: `1`) |

### `sovkora login [email] [password]`

Authenticate and cache JWT token

| Argument / option | Description |
|:---|:---|
| `[email]` | Account email address |
| `[password]` | Account password |

### `sovkora logs`

Stream and browse service logs

| Argument / option | Description |
|:---|:---|
| `--service <name>` | Tail a specific service (router, ollama, media-worker, image-worker, ui) |
| `--list` | List all available log files with sizes |
| `--since <duration>` | Show logs from last N hours (e.g., 1h, 30m) (default: `24h`) |
| `--tail <lines>` | Number of lines to show from end of file (default: `50`) |

### `sovkora mcp register`

Register a new private MCP server

| Argument / option | Description |
|:---|:---|
| `--auth-token <token>` | Optional authorization token/header |

### `sovkora models pull <id>`

Download an image model into ~/models/stable-diffusion and verify its checksums

| Argument / option | Description |
|:---|:---|
| `<id>` | Model id (see `sovkora models image list`) |

### `sovkora models use <id>`

Make an installed image model the catalogue's default — the one `sovkora start` serves

| Argument / option | Description |
|:---|:---|
| `<id>` | Model id (see `sovkora models image list`) |

### `sovkora pack validate <directory>`

Check a bundle's manifest, and whether this platform can run it

| Argument / option | Description |
|:---|:---|
| `<directory>` | The bundle directory holding pack.yaml |
| `--offline` | Check the manifest's shape only, without asking the platform |

### `sovkora pack install <directory>`

Install a bundle: capabilities, billing and catalogue, or none of them

| Argument / option | Description |
|:---|:---|
| `<directory>` | The bundle directory holding pack.yaml, or a directory of bundles with --all |
| `--all` | Install every bundle in the directory — what a fresh database needs |

### `sovkora pack publish <directory>`

Put an installed bundle's pack and Studios on the shelf

| Argument / option | Description |
|:---|:---|
| `<directory>` | The bundle directory holding pack.yaml |

### `sovkora pack list [directory]`

List the bundles this deployment's pack sources offer

| Argument / option | Description |
|:---|:---|
| `[directory]` | Look in this directory instead of the configured sources |

### `sovkora register [username] [email]`

Register a new user account

| Argument / option | Description |
|:---|:---|
| `[username]` | Desired username |
| `[email]` | Account email address |
| `-l, --language <lang>` | BCP 47 language tag (e.g. en, ja) |

### `sovkora release verify <manifest>`

Check a release manifest, and with --dir the artefacts of an offline bundle against it

| Argument / option | Description |
|:---|:---|
| `<manifest>` | Path to the release manifest (orazaka-release.json) |
| `--dir <path>` | Offline bundle directory whose artefacts are checked (size and SHA-256) |
| `--target <target>` | Only the artefacts of one target: mac-arm64, linux-amd64 or linux-arm64 |
| `--json` | Print the result as JSON |

### `sovkora reset [token]`

Reset account password using a valid reset token

| Argument / option | Description |
|:---|:---|
| `[token]` | Password reset token |

### `sovkora runs ls`

List the runs: name, status, current step, elapsed time, cost against the budget

| Argument / option | Description |
|:---|:---|
| `--watch` | Refresh the list every few seconds |
| `--json` | Output the rows as JSON |

### `sovkora runs show <ref> <ref>`

Show one run, by name or id

| Argument / option | Description |
|:---|:---|
| `<ref>` | The run name or id |
| `<ref>` | The run name or id |
| `--steps` | Show the folded timeline, one line per step |
| `--json` | Output the run as JSON |
| `--json` | Output the result as JSON |

### `sovkora runs send <ref> <message>`

Answer a run that is waiting for input: the message becomes the output of the step it waits on

| Argument / option | Description |
|:---|:---|
| `<ref>` | The run name or id |
| `<message>` | What to say to the run |
| `--json` | Output the run as JSON |

### `sovkora runs deny <ref>`

Refuse the approval a run is waiting on: the run stops without the step and its hold is released

| Argument / option | Description |
|:---|:---|
| `<ref>` | The run name or id |
| `--reason <text>` | Why the approval is refused |
| `--json` | Output the run as JSON |

### `sovkora runs wait <ref>`

Follow a run until it ends or needs your approval

| Argument / option | Description |
|:---|:---|
| `<ref>` | The run name or id |
| `--notify` | Send a desktop notification when it ends or needs approval |
| `--json` | Output the final row as JSON |

### `sovkora runs resume <ref>`

Start a new run from a step of a finished one; the steps before it keep their outputs

| Argument / option | Description |
|:---|:---|
| `<ref>` | The run name or id |
| `--json` | Output the new run as JSON |

### `sovkora settings set <key> <value>`

Set a user preference key-value pair

| Argument / option | Description |
|:---|:---|
| `<key>` | Preference key to set |
| `<value>` | Preference value to set |

### `sovkora start`

Start Sovkora infrastructure — dev mode (Docker middleware + AI engines) or full mode (also Router + UI)

| Argument / option | Description |
|:---|:---|
| `--mode <mode>` | Startup mode: dev (default, infra + AI engines) or full (also Router + Next.js UI) (default: `dev`) |
| `--only <service>` | Start only a specific service (postgres, redis, rabbitmq, ollama, localai, image-gen, media-worker) |
| `--skip-health` | Skip final health verification |
| `--wait-timeout <ms>` | Timeout for service startup (default: 60000ms) (default: `60000`) |
| `--allow-partial` | Allow partial startup (some services may fail) |
| `--verbose` | Show detailed startup logs |
| `--logs` | Tail log files after startup |

### `sovkora status`

Show live status of all Sovkora services

| Argument / option | Description |
|:---|:---|
| `--json` | Output status as JSON |
| `--watch` | Refresh every 5 seconds |

### `sovkora stop`

Teardown the Sovkora infrastructure — containers, workers, and state

| Argument / option | Description |
|:---|:---|
| `--purge` | Also purge database data and upload directories |
| `-y, --yes` | Skip confirmation prompts |

### `sovkora studio list`

Browse the Studio catalogue

| Argument / option | Description |
|:---|:---|
| `-p, --profession <trade>` | Filter by trade |

### `sovkora studio install <studioKey>`

Install a Studio into your workspace

| Argument / option | Description |
|:---|:---|
| `<studioKey>` | The Studio to install |
| `-c, --config <pairs>` | Configuration as key=value,key2=value2 |

### `sovkora studio run <target>`

Run an installed Studio — or, with --studio, a toolkit Studio by its key

| Argument / option | Description |
|:---|:---|
| `<target>` | The installation to run (or the Studio key with --studio) |
| `-i, --inputs <pairs>` | Inputs as key=value,key2=a\|b\|c (pipes make a list) |
| `-f, --inputs-file <file>` | Inputs as one JSON object (for prose with commas); --inputs pairs apply on top |
| `-s, --studio` | The target is a Studio key: a toolkit Studio (media, art direction) has no installation |
| `--background` | Print the run id and return at once; follow it with `sovkora runs ls` or `runs wait` |
| `--notify` | Send a desktop notification when the run ends or needs your approval |

### `sovkora studio logs <runId>`

Show a run's per-step state and results

| Argument / option | Description |
|:---|:---|
| `<runId>` | The run to inspect |

### `sovkora verify [token]`

Verify account registration token

| Argument / option | Description |
|:---|:---|
| `[token]` | Email verification token |

### `sovkora video <prompt>`

Generate video from a text prompt

| Argument / option | Description |
|:---|:---|
| `<prompt>` | Text prompt describing the desired video |
| `-d, --duration <seconds>` | Duration of the video in seconds (default: `4`) |
| `-m, --model <model>` | Specify AI model name |
| `-a, --aspect <ratio>` | Frame: 9:16 (default of the Studio), 16:9 or 1:1 — the model must make it |
| `-s, --seed <number>` | The same prompt, model and seed make the same shot again |
| `-o, --output <path>` | Specific file path to save the video |
