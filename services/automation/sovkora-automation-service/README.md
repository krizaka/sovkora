# Automation service

> `services/automation/sovkora-automation-service` of [Sovkora](../../../README.md), one repository (maven, ai layer). Depends on `build`, `engine`. Licence: [FSL-1.1-ALv2](../../../LICENSE).

Connector automation (Jira, Slack, WhatsApp, Messenger, CLI agents) with Quartz scheduling and execution telemetry.

## What it provides

Connector automation (port `8082`): consumes `job.automation.*`, dispatches to Jira / Slack /
WhatsApp / Messenger connectors or to a user's CLI agent (`job.agent.dispatch.{userId}`), Quartz
scheduling, `evt.automation.telemetry`. Own database `orazaka_automation_db`
(schema: Flyway `V1__automation_baseline.sql` in `src/main/resources/db/migration`, applied at
startup; `infra/initdb/50-automation.sql` only creates the role and the database locally). User-facing notifications moved to
[krizaka-notifications](https://github.com/krizaka/krizaka-java/blob/main/krizaka-service-kit/docs/notifications.md).

## Build and test

```bash
./mvnw verify -pl services/automation/sovkora-automation-service -am      # from the repository root: this module and what it needs
./mvnw verify -pl services/automation/sovkora-automation-service -amd     # this module and everything that depends on it
```

CI runs it on every pull request that touches `services/automation/sovkora-automation-service/` (ADR-029: one repository, one CI).
