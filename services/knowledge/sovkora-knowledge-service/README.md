<!-- krizaka-header -->
<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/sovkora-dark.svg">
  <img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/sovkora-light.svg" alt="Sovkora" width="96">
</picture>

# Sovkora Knowledge Service

**The AI that never leaves home.**

Knowledge & RAG retrieval service (pgvector) with asynchronous indexing.

[![CI](https://github.com/krizaka/sovkora-knowledge-service/actions/workflows/ci.yml/badge.svg)](https://github.com/krizaka/sovkora-knowledge-service/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/krizaka/sovkora-knowledge-service/badge)](https://scorecard.dev/viewer/?uri=github.com/krizaka/sovkora-knowledge-service)
[![License: FSL-1.1-ALv2](https://img.shields.io/badge/license-FSL--1.1--ALv2-blue.svg)](LICENSE.md)
[![Sovkora](https://img.shields.io/badge/part%20of-Sovkora-f67e23)](https://github.com/krizaka/sovkora#repositories)
[![Docs](https://img.shields.io/badge/docs-krizaka.com-f67e23)](https://www.krizaka.com/en/products/sovkora)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-knowledge-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-knowledge-service) [![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-knowledge-service&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-knowledge-service) [![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-knowledge-service&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-knowledge-service) [![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-knowledge-service&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-knowledge-service) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka-knowledge-service&metric=coverage)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka-knowledge-service)

[Documentation](https://www.krizaka.com/en/products/sovkora) · [Website](https://www.krizaka.com) · [Krizaka on GitHub](https://github.com/krizaka)

</div>
<!-- /krizaka-header -->

**Layer:** Sovkora AI engine · **Version:** `1.0.0-SNAPSHOT` · **License:** FSL-1.1-ALv2 ·
part of the [Sovkora platform](https://github.com/krizaka/sovkora) by [Krizaka](https://krizaka.com)

## What it provides

Retrieval over pgvector (port `8084`): `/internal/v1/knowledge/retrieve`,
`/internal/v1/knowledge/sources/search`, and AMQP-driven RAG indexing. Own database
`orazaka_knowledge_db`: the schema is Flyway's `V1__knowledge_baseline.sql`
(`src/main/resources/db/migration`), applied at startup; `infra/initdb/40-knowledge.sql` only
creates the role, the database and the `vector` extension for the local compose.

## Position in the platform

| | |
|:---|:---|
| Depends on | [`sovkora-build`](https://github.com/krizaka/sovkora-build) |
| Used by | _no other Sovkora repository._ |
| Workspace path | `orazaka-apps/services/orazaka-knowledge-service` |

## Build

**Inside the Sovkora workspace** (recommended — every dependency is built from source):

```bash
git clone https://github.com/krizaka/sovkora.git && cd sovkora
node scripts/workspace.mjs clone          # clones every repository at its workspace path
./mvnw -f orazaka-apps/services/orazaka-knowledge-service/pom.xml verify
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

## CI

The pipeline is the organisation's reusable [`java-ci.yml`](https://github.com/krizaka/.github/blob/main/.github/workflows/java-ci.yml): this service is verified inside the Sovkora workspace against its upstreams built from source. A change that touches only documentation skips the build and still reports green.

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

## Access (ADR-091)

Every read is made for a verified principal: the `act` claim of the core's signed service token
(tenant, subject, roles, groups). A source carries `tenant_id` and `acl_principals`
(`role:…`, `group:…`, `user:…`; default the owner alone); the tenant and the ACL are part of the SQL
`WHERE` and of the vector filter, so an unreadable passage is never fetched. A call that acts for
nobody is refused (403) and audited in `knowledge_access_audit` (principal, resource id, reason — never
the content). `PUT /internal/v1/knowledge/sources/{id}/acl` lets an administrator of the tenant change
who may read a source.
