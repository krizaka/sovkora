<!-- krizaka-header -->
<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/sovkora-dark.svg">
  <img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/sovkora-light.svg" alt="Sovkora" width="96">
</picture>

# Sovkora Platform Contracts

**The AI that never leaves home.**

Tier-1 platform contracts shared by every Sovkora service: the job plane (jobs-api) and the application-persistence ports (persistence-app-api). Pure interfaces and records, zero implementation.

[![CI](https://github.com/krizaka/sovkora-contracts/actions/workflows/ci.yml/badge.svg)](https://github.com/krizaka/sovkora-contracts/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/krizaka/sovkora-contracts/badge)](https://scorecard.dev/viewer/?uri=github.com/krizaka/sovkora-contracts)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Sovkora](https://img.shields.io/badge/part%20of-Sovkora-f67e23)](https://github.com/krizaka/sovkora#repositories)
[![Docs](https://img.shields.io/badge/docs-krizaka.com-f67e23)](https://www.krizaka.com/en/products/sovkora)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-contracts&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-contracts) [![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-contracts&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-contracts) [![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-contracts&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-contracts) [![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-contracts&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-contracts) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-contracts&metric=coverage)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-contracts)

[Documentation](https://www.krizaka.com/en/products/sovkora) · [Website](https://www.krizaka.com) · [Krizaka on GitHub](https://github.com/krizaka)

</div>
<!-- /krizaka-header -->

**Layer:** Foundation — reusable by any Krizaka application · **Version:** `1.0.0-SNAPSHOT` · **License:** Apache-2.0 ·
part of the [Sovkora platform](https://github.com/krizaka/sovkora) by [Krizaka](https://krizaka.com)

## What it provides

| Module | Artifact | Role |
|:---|:---|:---|
| `sovkora-jobs-api` | `com.krizaka.sovkora:sovkora-jobs-api` | Job plane contract: job commands/events, lanes, typed failure causes, capability descriptors. |
| `sovkora-persistence-app-api` | `com.krizaka.sovkora:sovkora-persistence-app-api` | Application-persistence ports & DTOs (chat, jobs, models, configs, outbox) implemented by `sovkora-persistence-app`. |

Domain contracts live with their domain: `krizaka-users-api` in
[krizaka-users](https://github.com/krizaka/krizaka-build/tree/main/krizaka-users), `krizaka-billing-api` in [krizaka-billing](https://github.com/krizaka/krizaka-build/tree/main/krizaka-billing),
`sovkora-studio-api` in [sovkora-studio-service](https://github.com/krizaka/sovkora-studio-service).

## Rules

- Tier-1 (AGENTS.md §2): **pure interfaces and records**, zero implementation dependency.
- Semver'd: a breaking change here is a breaking change for every service.

## Position in the platform

| | |
|:---|:---|
| Depends on | [`sovkora-build`](https://github.com/krizaka/sovkora-build) |
| Used by | [`sovkora-studio-service`](https://github.com/krizaka/sovkora-studio-service) · [`sovkora-engine`](https://github.com/krizaka/sovkora-engine) · [`sovkora-conversation-service`](https://github.com/krizaka/sovkora-conversation-service) · [`sovkora-job-service`](https://github.com/krizaka/sovkora-job-service) |
| Workspace path | `orazaka-libs/orazaka-contracts` |

## Build

**Inside the Sovkora workspace** (recommended — every dependency is built from source):

```bash
git clone https://github.com/krizaka/sovkora.git && cd sovkora
node scripts/workspace.mjs clone          # clones every repository at its workspace path
./mvnw -f orazaka-libs/orazaka-contracts/pom.xml verify
```

**Standalone** — upstream artifacts must be in `~/.m2` (built by the workspace) or resolvable from
GitHub Packages (`https://maven.pkg.github.com/krizaka/<repository>`, see the
[workspace README](https://github.com/krizaka/sovkora#consuming-packages)):

```bash
./mvnw verify
```

Requirements: JDK 21, Docker (Testcontainers integration tests).

## Governance

This repository follows the Sovkora governance contract — [AGENTS.md](https://github.com/krizaka/sovkora/blob/main/AGENTS.md)
in the workspace is normative; the local [AGENTS.md](AGENTS.md) only scopes it to this repository.

<!-- krizaka:workflows:start -->
## Workflows

What runs in the Actions tab of this repository.

- **CI** (`ci.yml`): Lint, tests and build of the repository. Runs on push, on every pull request, on demand.
- **Dependabot auto-merge** (`dependabot-automerge.yml`): Auto-merges safe Dependabot updates. Runs on every pull request.
- **Merge resolution** (`merge-resolution.yml`): Fails a PR that drops what main added without a deletion-explained line. Runs on every pull request.
- **Release notes** (`release-notes.yml`): Writes the release notes. Runs on push.
- **Scorecard** (`scorecard.yml`): OpenSSF Scorecard of the repository. Runs when branch rules change, on a schedule, on demand.
- **Security scan** (`security.yml`): Secret scan (gitleaks) and workflow scan (zizmor); both are required checks. Runs on every pull request, in the merge queue, on push.
<!-- krizaka:workflows:end -->

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

---

<sub>A Krizaka product · [krizaka.com](https://www.krizaka.com) · Apache-2.0</sub>
