# Sovkora contracts

> `contracts` of [Sovkora](../README.md), one repository (maven, foundation layer). Depends on `build`. Licence: [Apache-2.0](LICENSE).

Tier-1 platform contracts shared by every Sovkora service: the job plane (sovkora-jobs-api), the application-persistence ports (sovkora-persistence-api) and the secret sealing (sovkora-crypto). Pure interfaces and records.

## What it provides

| Module | Artifact | Role |
|:---|:---|:---|
| `sovkora-jobs-api` | `com.krizaka.sovkora:sovkora-jobs-api` | Job plane contract: job commands/events, lanes, typed failure causes, capability descriptors. |
| `sovkora-persistence-api` | `com.krizaka.sovkora:sovkora-persistence-api` | Application-persistence ports & DTOs (chat, jobs, models, configs, outbox) implemented by `sovkora-engine-persistence`. |

Domain contracts live with their domain: `krizaka-users-api` in
[krizaka-users](https://github.com/krizaka/krizaka-java/tree/main/krizaka-users), `krizaka-billing-api` in [krizaka-billing](https://github.com/krizaka/krizaka-java/tree/main/krizaka-billing),
`sovkora-studio-api` in [sovkora-studio-service](https://github.com/krizaka/sovkora/tree/main/services/studio).

## Rules

- Tier-1 (AGENTS.md §2): **pure interfaces and records**, zero implementation dependency.
- Semver'd: a breaking change here is a breaking change for every service.

## Build and test

```bash
./mvnw verify -pl contracts -am      # from the repository root: this module and what it needs
./mvnw verify -pl contracts -amd     # this module and everything that depends on it
```

CI runs it on every pull request that touches `contracts/` (ADR-029: one repository, one CI).
