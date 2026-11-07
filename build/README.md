# Sovkora build

> `build` of [Sovkora](../README.md), one repository (maven, foundation layer). Depends on nothing else of Sovkora. Licence: [Apache-2.0](LICENSE).

The parent POM sovkora-parent (krizaka-parent from Maven Central, the Spring Boot / Spring AI BOMs, the Sovkora BOM, the ADR-029 naming guards) and the shared governance test kit (ArchUnit rules, Testcontainers base).

## What it provides

| Module | Artifact | Role |
|:---|:---|:---|
| `sovkora-parent` | `com.krizaka.sovkora:sovkora-parent` (pom) | Java 21, Spring Boot 4.0 + Spring AI 2.0 BOMs, the **Sovkora BOM** (every component at one platform version), Spotless (google-java-format), JaCoCo, Surefire/Failsafe, `dev`/`staging`/`prod` profiles, GitHub Packages distribution. |
| `sovkora-test-support` | `com.krizaka.sovkora:sovkora-test-support` | The governance kit: ArchUnit rules (`GovernanceRules`, naming, tiers, security, outbox, metering…), SQL seam rules, Testcontainers singletons (`AbstractContainerIntegrationTest`), workspace locator. |

## Use it

Every Sovkora JVM repository inherits the parent:

```xml
<parent>
    <groupId>com.krizaka.sovkora</groupId>
    <artifactId>sovkora-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath/>
</parent>
```

and runs the governance rules from its tests:

```xml
<dependency>
    <groupId>com.krizaka.sovkora</groupId>
    <artifactId>sovkora-test-support</artifactId>
    <scope>test</scope>
</dependency>
```

A new Krizaka application can adopt the same parent to get the same stack, versions and
quality gates with zero copy-paste.

### Workspace-wide rules

Some rules are **cross-repository** by nature (no pack name in engine code, every capability has an
executor, one settlement author…). They need every repository on disk, so they run inside the
Sovkora workspace ([`krizaka/sovkora`](https://github.com/krizaka/sovkora)) and are reported as *skipped* in a standalone
clone — never as passed.

## Build and test

```bash
./mvnw verify -pl build -am      # from the repository root: this module and what it needs
./mvnw verify -pl build -amd     # this module and everything that depends on it
```

CI runs it on every pull request that touches `build/` (ADR-029: one repository, one CI).
