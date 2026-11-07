<!-- krizaka-header -->
<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/sovkora-dark.svg">
  <img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/sovkora-light.svg" alt="Sovkora" width="96">
</picture>

# Sovkora Automation Service

**The AI that never leaves home.**

Connector automation (Jira, Slack, WhatsApp, Messenger, CLI agents) with Quartz scheduling and execution telemetry.

[![CI](https://github.com/krizaka/sovkora-automation-service/actions/workflows/ci.yml/badge.svg)](https://github.com/krizaka/sovkora-automation-service/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/krizaka/sovkora-automation-service/badge)](https://scorecard.dev/viewer/?uri=github.com/krizaka/sovkora-automation-service)
[![License: FSL-1.1-ALv2](https://img.shields.io/badge/license-FSL--1.1--ALv2-blue.svg)](LICENSE.md)
[![Sovkora](https://img.shields.io/badge/part%20of-Sovkora-f67e23)](https://github.com/krizaka/sovkora#repositories)
[![Docs](https://img.shields.io/badge/docs-krizaka.com-f67e23)](https://www.krizaka.com/en/products/sovkora)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-automation-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-automation-service) [![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-automation-service&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-automation-service) [![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-automation-service&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-automation-service) [![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-automation-service&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-automation-service) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-automation-service&metric=coverage)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-automation-service)

[Documentation](https://www.krizaka.com/en/products/sovkora) · [Website](https://www.krizaka.com) · [Krizaka on GitHub](https://github.com/krizaka)

</div>
<!-- /krizaka-header -->

**Layer:** Sovkora AI engine · **Version:** `1.0.0-SNAPSHOT` · **License:** FSL-1.1-ALv2 ·
part of the [Sovkora platform](https://github.com/krizaka/sovkora) by [Krizaka](https://krizaka.com)

## What it provides

Connector automation (port `8082`): consumes `job.automation.*`, dispatches to Jira / Slack /
WhatsApp / Messenger connectors or to a user's CLI agent (`job.agent.dispatch.{userId}`), Quartz
scheduling, `evt.automation.telemetry`. Own database `orazaka_automation_db`
(schema: Flyway `V1__automation_baseline.sql` in `src/main/resources/db/migration`, applied at
startup; `infra/initdb/50-automation.sql` only creates the role and the database locally). User-facing notifications moved to
[krizaka-notifications](https://github.com/krizaka/krizaka-build/blob/main/krizaka-platform-kit/docs/notifications.md).

## Position in the platform

| | |
|:---|:---|
| Depends on | [`sovkora-build`](https://github.com/krizaka/sovkora-build) · [`krizaka-billing`](https://github.com/krizaka/krizaka-build/tree/main/krizaka-billing) |
| Used by | _no other Sovkora repository._ |
| Workspace path | `orazaka-apps/services/orazaka-automation-service` |

## Build

**Inside the Sovkora workspace** (recommended — every dependency is built from source):

```bash
git clone https://github.com/krizaka/sovkora.git && cd sovkora
node scripts/workspace.mjs clone          # clones every repository at its workspace path
./mvnw -f orazaka-apps/services/orazaka-automation-service/pom.xml verify
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

[FSL-1.1-ALv2](LICENSE.md) (Functional Source License, Apache-2.0 future licence) © Technologies Krizaka Inc. — read,
run, modify and self-host it for any purpose except offering a competing service; each version becomes Apache-2.0 two
years after its release. Commits before the licence change remain [Apache-2.0](LICENSE-APACHE) — see [NOTICE](NOTICE)
and [ADR-012](https://github.com/krizaka/.github/blob/main/docs/adr/ADR-012-licensing-libraries-open-products-fair-source.md).
The Krizaka names and birds are not licensed: [trademark policy](https://github.com/krizaka/.github/blob/main/TRADEMARKS.md).
