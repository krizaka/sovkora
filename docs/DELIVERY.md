---
title: "Delivery — how Sovkora is packaged, signed, installed and updated on a client's premises"
description: "The delivery targets of Sovkora (one Mac, a Linux/NVIDIA server, later Kubernetes), how each is built, signed and updated, the release train, offline licences, backups, support bundles and the Loi 25 posture."
category: Guidelines
order: 20
---

# Delivery

> **Status: design, decided 2026-10-14 (ORA-PACKAGING).** Step 1 (the release manifest and
> `orazaka release verify`) is implemented in `orazaka-cli`; every other step is an issue of the
> epic linked at the end. Until a step lands, AGENTS.md §0 still governs: this document lifts a
> restriction only through the step that needs it (images to GHCR in step 3, for instance).

Sovkora is sold first as **sovereign AI on the client's premises**: Montréal firms (law, accounting,
private clinics) that cannot send their documents to a US cloud under the **Loi 25** (strategy, year 1:
2–3 pilot licences at 1 500–3 000 CAD/year). "Delivery" therefore means: a firm with no DevOps staff
gets a working Sovkora on hardware it owns, keeps it up to date, and never sends a byte of its data
anywhere — and we can prove each of those three things.

Everything below reuses what exists: the `orazaka` CLI (`install`, `start`, `stop`, `doctor`, `models`,
`pack`), `infra/docker-compose.yml`, the single JVM image of `infra/deploy/Dockerfile`, the CUDA image of
the media worker (`ghcr.io/krizaka/sovkora-media-worker:cuda-*`, ADR-077), the native Metal path
(Ollama, stable-diffusion.cpp `sd-server`, the media worker on MLX/Metal, LocalAI for speech, ADR-075/076),
`models.yaml` (pinned revisions, commercial licences only) and the pack catalogue with its entitlements.

## 1. The targets at a glance

| Target | For | Installer | Containers | AI runtime | Updates | When |
|---|---|---|---|---|---|---|
| **Sovkora for Mac** | a firm of 1–15 people, one Apple Silicon Mac (Mac mini / Studio) | **Sovkora Desktop** (Tauri 2) driving the CLI | Colima VM (arm64), managed by Sovkora | native on Metal: Ollama, `sd-server`, media worker, LocalAI | Desktop → `orazaka update` | first pilots (2027-Q1 target) |
| **Sovkora on a Linux server** | an office server or a client VM, optionally with an NVIDIA GPU | `npx orazaka install --server` (or the `install.sh` bootstrap) | Docker Engine + compose bundle, systemd | Ollama container, CUDA media-worker image (or CPU profile) | `orazaka update` (CLI) | right after the Mac |
| **Kubernetes / Helm** | bigger organisations, HA | Helm chart | per-service images | GPU operator | Helm upgrade | **later** — first client that needs HA |

One **release** (one version number) covers every target; a target is a subset of its artefacts.

## 2. Sovkora for Mac (Apple Silicon)

**What the firm receives:** `Sovkora.dmg`. It opens on a Tauri 2 app (system WebView, ~10 MB of Rust),
signed with Krizaka's **Developer ID Application** certificate, hardened runtime, **notarized** and
stapled, so Gatekeeper opens it without a warning. The app is the installer *and* the control panel;
it never re-implements an operation — it runs the CLI as a **sidecar** and renders its JSON output.

**The sidecar.** The CLI is Node.js with one native addon (`better-sqlite3`), so a single-file
executable is not dependable yet. The app ships a pinned **Node 22 runtime** (the sidecar binary,
`node-aarch64-apple-darwin`) plus the CLI's `dist/` and production `node_modules` in its resources;
every Mach-O inside (node, `better_sqlite3.node`, `sd-server`, colima, lima, docker) is signed with the
same identity, which notarization requires. A Node single-executable build is re-evaluated when the
addon can be dropped.

**Containers: Colima, chosen.** The platform's stateful services and JVMs run in containers; on a Mac
that needs a Linux VM. We ship and manage **Colima** (MIT, on Lima, Apple Virtualization.framework,
`virtiofs`, no QEMU) in a **dedicated profile** (`colima start orazaka --vm-type vz --arch aarch64`) with
the Docker CLI and the compose plugin (Apache-2.0) next to it — all redistributable, all scriptable, no
account, no GUI.

| Option | Why not (for the default) |
|---|---|
| Docker Desktop | Not redistributable; paid subscription for organisations > 250 people or > 10 M USD revenue; its own updater and UI outside our control. Detected and used if the firm already runs it. |
| OrbStack | Excellent, but a paid commercial licence per user — a cost on every client. Detected and used if present. |
| Apple `container` (macOS 26) | Native and promising, but young, macOS 26 only and no compose. Re-evaluated in 2027. |

The VM gets a fixed budget from the machine profile (e.g. 6 vCPU / 12 GB on a 32 GB Mac); Postgres,
Redis, RabbitMQ, the service images and the web apps run there, on **linux/arm64** images — which is why
every image of a release that targets the Mac must exist for arm64 (a rule `orazaka release verify`
enforces).

**AI runs natively, on Metal.** A VM on macOS has no GPU, so generation stays outside it, exactly as in
development today: **Ollama** (LLM and embeddings), **`sd-server`** built for Metal in our release CI
(not compiled on the client), the **media worker** (Python, MLX/Metal) in a venv created by `uv` from a
pinned lock and a wheelhouse shipped in the release (no PyPI at install time), and **LocalAI** for
speech. Each is a **launchd agent** (`~/Library/LaunchAgents/com.krizaka.orazaka.*.plist`) written by the
CLI: it restarts on crash and at login, binds `127.0.0.1` only, and the containers reach it through
`host.lima.internal`. No shell scripts (ERR-125): the CLI writes the units.

**Models, once, with progress.** `orazaka models pull --profile mac-16|mac-32|mac-64` fetches the weights
the profile needs from the pinned revisions of the release manifest (Hugging Face, Ollama registry),
verifies each file's SHA-256, and reports progress as JSON lines the Desktop app turns into a progress
bar. For a firm with no outbound access, `orazaka models import <dir>` reads the same files from an
**offline kit** (a USB SSD we prepare). After that, Sovkora runs with the network cable unplugged.

**Hardware floor.** Apple Silicon only (M1 or later), macOS 14+, 16 GB RAM minimum (small models), 32 GB
recommended, 150 GB free disk for the bundle and models. `orazaka doctor` checks all of it, plus
**FileVault on** (encryption at rest is part of the Loi 25 posture).

**Where things live.** `~/Library/Application Support/Sovkora/` — `config/` (`.env`, mode 600), `models/`,
`backups/`, `logs/`, `releases/<version>/` (the current and the previous release, for rollback). The
database volumes live in the Colima VM disk; backups leave it as `pg_dump` archives (§6).

## 3. Sovkora on a Linux server (and NVIDIA boxes)

**What the firm (or its IT provider) receives:** `orazaka-server-<version>-linux-<arch>.tar.gz` — the
CLI with its Node runtime, the **compose bundle**, the systemd units, the release manifest and its
signature, the checksums and SBOMs. An **offline** variant adds `images.tar.zst` (`docker save` of every
image pinned in the manifest) and the model kit.

**Install, one command:** `npx orazaka install --server` on a machine with Node 22, or
`curl -fsSL https://www.sovkora.com/install.sh | sh` — a thin bootstrap that downloads the server
tarball, checks its SHA-256 against the signed manifest and hands over to the CLI; every step of the
installation is the CLI's (ERR-125), the script only fetches it. Offline: `sudo ./orazaka install
--server` from the unpacked tarball. It:

1. runs `orazaka doctor --server`: Ubuntu 24.04 LTS / Debian 12 / RHEL 9, Docker Engine ≥ 27 with
   the compose plugin (free; Docker Desktop is not used on servers), disk, RAM, LUKS on the data volume
   (warning), and with a GPU: `nvidia-smi`, driver ≥ 570 (CUDA 12.8), NVIDIA Container Toolkit runtime;
2. creates the `orazaka` system user and the layout `/opt/orazaka/<version>` (code, current → symlink),
   `/etc/orazaka` (config, secrets generated at install, mode 600), `/var/lib/orazaka` (data, models,
   backups);
3. loads or pulls the images **by digest** (never by tag) and verifies their signatures;
4. writes `orazaka.service` (compose up/down, `Type=oneshot` + `RemainAfterExit`) and
   `orazaka-backup.timer` (nightly), enables them, starts, and waits for every health check.

**Profiles of the compose bundle:** `core` (Postgres + pgvector, Redis, RabbitMQ, the service images,
edge, web, admin), `gpu` (the CUDA media-worker image — worker + `sd-server`, ADR-077 — and Ollama with
`--gpus all`), `cpu` (Ollama and the media worker on CPU: text, documents and small images only — said
honestly in the profile). TLS on the LAN: the edge behind Caddy with the firm's certificate, or an
internal CA created at install whose root the firm distributes.

No development fixture ever ships: `00-reset.sql` is excluded from the
bundle; the first administrator is created by `orazaka install` and sign-up stays closed
(`AUTH_SIGNUP_ENABLED=false`).

## 4. Later: Kubernetes / Helm

Not now. When a client needs high availability or more than one GPU node: the same per-service images
(§6), a Helm chart in this repository,
external managed Postgres, the NVIDIA GPU operator, the same release manifest feeding the chart's
`values.yaml` digests. The release train and the signatures below are designed so this adds a target,
not a second process.

## 5. The release train — one version for the bundle

- **One version, SemVer**, for Sovkora as delivered (`1.0.0`, `1.1.0`, `1.1.1`; candidates `1.1.0-rc.1`).
  Components keep their own versions; the **release manifest** `orazaka-release.json` pins, for that
  release, each component's commit, each image by **digest**, each native artefact (Desktop dmg,
  `sd-server`, wheelhouse, server tarball) by **SHA-256 and size**, each model by **revision** with its
  licence (commercial use only), each pack by version, the CLI version and the oldest release it
  upgrades from in place (`upgradeFrom`).
- **Built by one workflow** in this repository on a tag `vX.Y.Z`: it reads `modules.json`,
  checks out every repository at the commit the release names, builds and tests (the existing reactor),
  builds **one public image per service and worker** on GHCR (`ghcr.io/krizaka/<component>`,
  multi-arch `linux/amd64` + `linux/arm64`; the media worker also as a CUDA `amd64` variant), the Metal
  `sd-server` and the dmg on a macOS arm64 runner, writes the manifest, signs everything and publishes a
  **GitHub Release** (free for a public repository) with the manifest, signatures, SBOMs and artefacts.
- **Cadence:** a minor release monthly when something changed, patches as needed; a release is a
  candidate first, installed on our own Mac and on an hourly GPU droplet (budget rules apply) before
  being promoted to `stable`.
- **Gate:** `orazaka release verify <manifest> [--dir <bundle>]` (implemented) refuses a manifest that is
  not complete and pinned, a channel/version mismatch, an image a target cannot run (no arm64 variant
  for a Mac), a model without a commercial licence, a duplicate, a path that leaves the bundle, and —
  with `--dir` — any artefact whose size or SHA-256 differs.

## 6. Cross-cutting

### Signatures and supply chain

| Artefact | Signed with | Verified by |
|---|---|---|
| Container images | **cosign keyless** (GitHub OIDC → Sigstore), plus SLSA build provenance (`actions/attest-build-provenance`) | `orazaka install/update` (online), digest pinning in the signed manifest (offline) |
| Release manifest | **ed25519 release key** (Krizaka); public key embedded in the CLI | the CLI, offline, with Node's own crypto — no network, no extra dependency |
| Sovkora Desktop | Apple **Developer ID Application** + **notarization** (notarytool) + staple; app updates through the Tauri updater (its own ed25519 key) | Gatekeeper; the Tauri updater |
| Server tarball, wheelhouse, `sd-server` | SHA-256 in the signed manifest; `sd-server` (macOS) also codesigned | `orazaka release verify --dir` |
| Commercial packs | signed by the pack key; installed only with a valid licence | the CLI and the pack installer |

The offline root of trust is therefore **one public key in the CLI**: a valid manifest signature
proves every digest and hash it lists. **SBOMs** (CycloneDX, `syft`) are produced for every image, the
dmg and the CLI, attached to the release and attested; a vulnerability scan (Grype/Trivy) blocks a
release on a critical finding that has a fix.

**Images are shared, public and pinned.** Every service and worker image is published to
**GHCR** (`ghcr.io/krizaka/orazaka-edge`, `…/orazaka-studio`, `…/orazaka-job-service`,
`…/orazaka-knowledge-service`, `…/orazaka-automation-service`, `…/orazaka-conversation-service`,
`…/orazaka-web-client`, `…/orazaka-worker-media` with its `cuda-` variant, and the
Krizaka blocks the platform runs — users, billing, notifications), public, built by CI on release tags
only, signed with cosign keyless and attested (SBOM + SLSA provenance). The compose bundle references
them **by digest**, and `orazaka install`/`update` verify each signature (identity: the release
workflow of `krizaka/<repo>`) before anything runs. The single JVM image of `infra/deploy/Dockerfile`
stays what it is: the cheap shape of the **dev** environment, not a delivery artefact.

### Security by default

| Default | How | Checked by `orazaka doctor` |
|---|---|---|
| No default password | every secret (database roles, broker, JWT keys, asset master key, first admin) generated at install into one local file, mode 600, owned by the service user | file mode/owner; no known default value anywhere |
| Nothing exposed but the edge | Postgres, Redis, RabbitMQ, Ollama, workers bound to `127.0.0.1` or the internal compose network; only the edge publishes a port | listening sockets |
| TLS for remote access | Caddy in front of the edge (automatic certificate when the host has a public name, internal CA on a LAN), or a documented reverse proxy the firm already runs | edge reachable over HTTPS only when remote access is on |
| Hardened containers | non-root user, read-only root filesystem where the service allows it (tmpfs for scratch), `no-new-privileges`, all capabilities dropped, minimal base images (distroless/Alpine JRE, slim Python) | compose settings of the running containers |
| Scanned images | Trivy/Grype in CI, **fails on a critical** finding that has a fix | image digests match the signed manifest |
| Sign-up closed | `AUTH_SIGNUP_ENABLED=false`; the first administrator is created at the first run | flag value |

How to report a vulnerability and which versions are supported: [`SECURITY.md`](../SECURITY.md).

### Update and rollback

`orazaka update [--to X.Y.Z]` (Desktop: "Update" button, which runs it):

1. fetches the manifest (or reads it from an offline bundle), **verifies its signature**, checks
   `upgradeFrom` against the installed version (otherwise: the path of intermediate releases);
2. takes a **mandatory pre-update backup** (below);
3. pulls/loads the new images by digest, installs the new native artefacts next to the old ones;
4. applies the **database migrations** recorded in a ledger (today `infra/migrations/*.sql` are applied
   by hand — a versioned ledger is a prerequisite step), then switches the `current` pointer and
   restarts; health checks gate the switch.

`orazaka rollback` returns to the previous release kept on disk **and restores the pre-update backup**
— migrations are forward-only, so rolling back code means rolling back data to that moment, and the
command says so before it runs. Nothing updates by itself: an update check happens only when the firm
asks (button or command).

### Licence and pack entitlement — offline

The engine stays Apache-2.0 (docs/LICENSING.md); what a firm pays for is the **support licence** and
the **commercial packs**. A paid pack is unlocked **without any call home**:

- Krizaka issues `licence.json`: client, installation id (generated at install, shown by
  `orazaka doctor`), the pack keys entitled, support tier, validity dates — **signed with the licensing
  ed25519 key**, whose private half never leaves Oussama's machine (licences are issued there, not by
  CI).
- `orazaka licence install licence.json` verifies the signature and the installation id, then writes the
  entitlements through the existing billing model (`billing_pack_subscription` →
  `billing_pack_entitlement` → `EntitlementSnapshot.allows`), so a Studio is unlocked exactly as in the
  hosted case. The commercial pack bundle itself comes signed, from the private registry with the
  licence as bearer, or on the offline kit.
- **Expiry is soft:** 30 days of grace, then commercial Studios refuse *new* runs; nothing is deleted,
  every result and document stays readable and exportable. Data is never held hostage.

### Telemetry: none

Sovkora sends **nothing** to Krizaka: no analytics, no crash reports, no licence check, no automatic
update check. Third-party switches are forced off in the bundle (`HF_HUB_DISABLE_TELEMETRY=1`,
`NEXT_TELEMETRY_DISABLED=1`, Ollama bound to localhost). The only outbound traffic is the one the firm
triggers: model downloads at install, an update check, a pack download. `orazaka doctor` lists the
egress the installation is configured for, so the firm's IT can firewall everything else.

### Support bundle

`orazaka doctor --bundle` writes `orazaka-support-<date>.tar.gz` **locally**: the doctor report (JSON),
versions and the installed manifest, container and launchd states, health checks, disk/RAM/GPU, and the
last lines of each log at WARN and above **with secrets and personal data redacted** (tokens, keys,
e-mail addresses, file names of documents). It never contains the database, documents, prompts,
outputs or the `.env` values (keys only). The firm reads it, then sends it by its own means — Sovkora
never uploads it.

### Data, backup and restore

- `orazaka backup` — `pg_dump -Fc` of each bounded context's database, the encrypted assets
  (docs/ASSET_ENCRYPTION.md), the configuration; **the asset master key is exported separately** (a
  backup and its key are never stored together). Nightly by the timer/launchd, retention 7 daily + 4
  weekly, to `backups/` and optionally to the firm's NAS (`--to <path>`).
- `orazaka restore <archive>` — on the same or a new machine, after `install`; the release CI runs a
  **restore drill** (backup → wipe → restore → health) so a backup is known to restore.

### Loi 25 posture

- **Data stays on site.** Documents, embeddings, prompts and outputs are processed and stored on the
  firm's hardware; models run locally; no cross-border transfer by the software (no s. 17 assessment
  needed for Sovkora itself).
- **Krizaka has no access** by default — no remote access, no telemetry; support works from a support
  bundle or a screen share the firm opens.
- **Security by default:** encryption at rest (FileVault/LUKS checked by `doctor`, assets encrypted by
  the platform), sign-up closed, services bound to localhost or the LAN, TLS on the LAN, audit trail
  without content.
- **For the firm's privacy impact assessment (EFVP, s. 3.3)** we provide a technical sheet: data flows,
  storage locations, retention settings, sub-processors (none at runtime), what leaves the machine
  (nothing by default).

## 7. Costs and what Oussama provides

| Item | Cost | Needed for |
|---|---|---|
| **Apple Developer Program** (account pending activation) → Developer ID Application certificate + App Store Connect API key for `notarytool` | 99 USD / year | Desktop signing and notarization (step 11) |
| Release ed25519 key, Tauri updater key, licensing key — **generated by Oussama**, private halves in his password manager; release and updater keys as GitHub secrets, licensing key never in CI | 0 | steps 2, 11, 12 |
| GitHub Actions (macOS arm64 and Linux runners, public repositories), GHCR (public images), GitHub Releases, Sigstore | 0 | the release train |
| Colima, Lima, Docker Engine/CLI, Ollama, stable-diffusion.cpp, LocalAI | 0 (open source) | runtime |
| Hourly GPU droplet to test the CUDA path before a release | within the existing GPU budget (≤ 40 USD for the first batch) | candidates |
| Offline kit (USB SSD, 1–2 TB) per client without Internet | ~100–150 CAD, billed to the client | air-gapped installs |
| Commercial terms: support licence and pack EULA (legal review) | lawyer time | before the first paid pilot |

No new recurring cloud cost: delivery runs on GitHub's free tier for public repositories.

## 8. Build steps, in order

Each step is an issue of the epic, with its acceptance criteria. Steps marked **(Oussama)** need him.

1. Release manifest + `orazaka release verify` — **done** (orazaka-cli).
2. Signed release manifest (ed25519, public key in the CLI) — **(Oussama: generate the key)**.
3. Release images: one public image per service and worker on GHCR, multi-arch (+ CUDA worker), cosign,
   SBOM, provenance, scan; install verifies signatures.
4. Release workflow `vX.Y.Z` → manifest, artefacts, GitHub Release.
5. Versioned migration ledger (prerequisite of update).
6. On-prem compose bundle (profiles core / gpu / cpu, no dev fixtures, TLS).
7. `orazaka install --server` (+ `install.sh` bootstrap) + systemd + server checks in `doctor`.
8. `orazaka backup` / `restore` + timers + restore drill in CI.
9. `orazaka update` / `rollback`.
10. Models by profile with progress + offline kit import.
11. Mac runtime: Colima profile, launchd agents, Metal `sd-server` and wheelhouse in release CI.
12. Sovkora Desktop (Tauri 2) installer + control panel, signed and notarized — **(Oussama: Apple
    Developer ID)**; built by the ORA-DESKTOP session.
13. Offline licence + commercial pack entitlement — **(Oussama: licensing key, commercial terms)**.
14. `orazaka doctor --bundle` + egress/telemetry and encryption checks.
15. Loi 25 technical sheet for clients' EFVP.
16. Later: Helm chart.
17. Security by default (secrets generated, localhost binding, TLS, hardened non-root containers,
    scan gate) checked by `orazaka doctor`. `SECURITY.md` is written (this document's PR); the defaults and
    their checks are the issue.
18. "Run Sovkora on your own infrastructure" guide ([`SELF_HOSTING.md`](SELF_HOSTING.md)) in the README,
    published on dev.sovkora.com/docs.
