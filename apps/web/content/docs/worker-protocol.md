# Orazaka — Worker protocol (v1)

> **This document is the extension point.** Not `JobExecutor`, not any Java interface. A worker is
> **anything that honours the contract below** — the existing media worker is Python, links no
> Orazaka library, and participates fully (ADR-037 §3.4, ADR-038).
>
> The Java `JobExecutor` SPI is a _convenience_ for executors cheap enough to run inside the job
> service. It is not the mechanism, and a worker that never touches Java is not a lesser citizen.

**Version 1.** Additive changes (a new optional payload key, a new consumption measurement) do not
bump it. A change to an exchange, a routing-key grammar, or the meaning of an existing field does.

---

## 1. Topology

Two topic exchanges, both durable (AGENTS.md §6):

| Exchange         | Direction         | Carries            |
| :--------------- | :---------------- | :----------------- |
| `orazaka.jobs`   | platform → worker | work requests      |
| `orazaka.events` | worker → platform | lifecycle outcomes |

Routing keys are `job.{capability}.{action}` inbound and `job.{jobId}.{progress\|done\|error}`
outbound. A dead-letter exchange `orazaka.dlx` (direct) backs every queue.

**A worker declares which keys it drains and binds its own queues.** It MUST NOT assume the
platform has declared them: declare the exchange and the queue idempotently at startup so the
worker can boot before the platform does.

Queue arguments MUST match the platform's, or RabbitMQ refuses the re-declaration with
`PRECONDITION_FAILED`:

```
x-max-length             (BROKER_QUEUE_MAX_LENGTH, default 1000)
x-overflow               reject-publish
x-dead-letter-exchange   orazaka.dlx
x-dead-letter-routing-key <queue-name>
```

DLQ naming is `<queue>.dlq`, bound to `orazaka.dlx` with the queue's own name as routing key.

---

## 2. Inbound — the job command

The message body is JSON. Fields a worker MAY rely on:

| Field        | Type   | Meaning                                                         |
| :----------- | :----- | :-------------------------------------------------------------- |
| `jobId`      | string | **MUST** echo in every outcome event. The correlation identity. |
| `userId`     | string | Opaque actor id. Never a name, never an email.                  |
| `featureKey` | string | The capability. **A worker MUST NOT branch on it** — see §7.    |
| `model`      | string | Resolved model, or `"default"` to let the worker choose.        |
| `payload`    | object | The capability's arguments.                                     |

Inside `payload`, by convention: `prompt`/`text`, `filePath`, `imagePath`, `durationSeconds`,
`voice`, plus `runId`/`stepId`/`ordinal` when the job came from a Studio run, and any
`orazaka.*`-namespaced preference keys the producer stamped.

`holdId` is present **only** when the job was metered. Its **absence means "never authorised"** and
is not the same as null — a worker MUST NOT invent one, and MUST echo it back when present so the
credit hold is settled or released.

**Prefetch MUST be 1** for workers doing heavy inference: the accelerator is the scarce resource and
prefetch is the only backpressure that respects it.

---

## 3. Outbound — lifecycle events

Published to `orazaka.events`:

| Routing key            | When                          | Body                                            |
| :--------------------- | :---------------------------- | :---------------------------------------------- |
| `job.{jobId}.progress` | optional, any number of times | `{"jobId","progress"}` — integer percent        |
| `job.{jobId}.done`     | exactly once, terminal        | `{"jobId","result",["consumption"],["holdId"]}` |
| `job.{jobId}.error`    | exactly once, terminal        | `{"jobId","error",["holdId"]}`                  |

A worker MUST emit exactly one terminal event per job. `result` is free-form per capability; by
convention it carries `url` and `format` for generated media.

**A failed job MUST still carry its `holdId`**, so billing releases the reservation. A failure that
loses the hold freezes a paying actor's balance until the sweeper expires it.

### 3.0 Connecting

The broker credentials are the **`RABBITMQ_*`** family: `RABBITMQ_HOST`, `RABBITMQ_PORT`,
`RABBITMQ_USER`, `RABBITMQ_PASS`. Read those. `SPRING_RABBITMQ_*` and `RABBITMQ_PASSWORD` are
deprecated aliases that the reference workers still read **for one version**, with a warning; they
stop being read after 1.1.0.

### 3.1 A failure MUST declare its cause

`job.{jobId}.error` MUST carry a `cause`, from this closed vocabulary and nothing else:

| `cause`                | Means                                          | The run then                 |
| :--------------------- | :--------------------------------------------- | :--------------------------- |
| `GUARD_REFUSAL`        | a gate declined to serve, on purpose           | releases everything          |
| `INPUT_INVALID`        | **you checked the payload and it is unusable** | **settles what it measured** |
| `EXECUTOR_FAULT`       | you broke: a defect, an unhandled case         | releases everything          |
| `PLATFORM_UNAVAILABLE` | something you depend on was not there          | releases everything          |
| `TIMEOUT`              | the work did not finish inside its bound       | releases everything          |

**You are the only party that knows.** By the time the saga sees your event, the executor that
knew why is gone; before ADR-053 it read your `error` string and guessed, and guessing about
somebody's bill is what this replaces. The saga reads `cause` and never the message.

**`INPUT_INVALID` is the only cause that bills**, and it is the one you must be most careful with.
Set it only where your worker _positively validated_ the payload and found it unusable — a missing
required field, an unreadable asset you opened, a jurisdiction you do not carry. **Never set it
from a bare `except`**: an exception that merely happened while user data was in scope is as likely
to be your own defect, and the reference worker enforces this structurally — `INPUT_INVALID` is
raised only from its own `InvalidJobPayload` type, which the catch-all cannot reach.

**Say nothing and you say `EXECUTOR_FAULT`.** An absent or unrecognised cause degrades to the
reading that releases the hold and blames nobody. That is deliberate: a worker that has not been
taught this contract keeps working and costs its users nothing.

---

## 4. Consumption reporting

A worker reports **measurements**, never money and never a unit name:

```json
{ "metrics": { "frames": 48, "fps": 12, "images": 1, "steps": 20, "characters": 1200 } }
```

Recognised keys: `frames`, `fps`, `images`, `steps`, `width`, `height`, `characters`, `tokens`.
The platform adds wall-clock `gpuSeconds` itself.

The billable unit belongs to the pricebook row the hold was pinned to (ADR-033). A worker that
priced itself would be a second, drifting copy of the pricebook.

**Absent measurements are meaningful**: an unmeasured job is _released_, not billed at its estimate.
Reporting nothing costs the platform, which is the right direction for the error to run.

---

## 5. Idempotency, retry, dead-lettering

- The AMQP `messageId` header is the **idempotency key**. A worker MUST tolerate redelivery: either
  deduplicate on it, or make execution naturally idempotent.
- Retries are exponential; exhausted retries dead-letter to `<queue>.dlq`.
- A worker MUST ack after reaching a terminal outcome, **including on failure** — a job that cannot
  succeed will not succeed on redelivery, and nacking it forever is how a queue stops draining.

---

## 6. Registration and heartbeat

A worker declares itself in a `worker.yaml` beside its source:

```yaml
name: orazaka-worker-media # stable identity; the registry's primary key
family: media # coarse label; see the note below
bindings: # what it drains — never a capability name
  - "job.video.*"
  - "job.compose.*"
version: "1.0.0"
concurrency: 1
```

It reads that file at boot, **binds its queues from it**, and posts it to
`POST /internal/v1/workers`, then `POST /internal/v1/workers/{name}/heartbeat` periodically
(30 s is the reference cadence; the platform's staleness horizon is several multiples of it).
Both require the `SERVICE` bearer token (ADR-035), supplied as `ORAZAKA_SERVICE_TOKEN`.

> **Registration MUST NOT be a startup dependency.** A worker that cannot reach the job service MUST
> keep consuming. The registry is an availability signal, never an authorisation — dispatch is
> decided by `routing_key` alone and never consults it. Getting this backwards turns an
> observability feature into an outage.

`family` is a **coarse descriptive label** and does not identify who serves a capability: the seeds
carry one family across three routing keys and two processes. `bindings` is the exact answer, and is
what the coherence rule reads.

---

## 7. What a worker must never do

- **Never branch on `featureKey`.** Naming a capability couples a general-purpose worker to one pack
  and is refused by the build ([PACK-002]/[PACK-003]). Decide from the **routing key** the broker
  delivered under, or from the payload.
- **Never hard-code bindings in source.** They belong in `worker.yaml`.
- **Never invent a `holdId`**, and never drop one on failure.
- **Never report a billable unit or a credit amount.**

---

## 8. Conformance checklist

A new worker is conformant when all of these hold:

|  #  | Requirement                                                                     | Verified by                                                                                                                  |
| :-: | :------------------------------------------------------------------------------ | :--------------------------------------------------------------------------------------------------------------------------- |
|  1  | Declares exchanges and queues idempotently, with the platform's queue arguments | `AmqpContractIT.exchangesMatchContract`, `queuesAndDlqsExist`                                                                |
|  2  | Consumes its declared bindings from `orazaka.jobs`                              | `AmqpContractIT.jobCommandContractRoundTrip`                                                                                 |
|  3  | Job command shape is honoured                                                   | `AmqpContractIT` + `amqp-contracts/job.command.json` fixture                                                                 |
|  4  | Emits exactly one terminal `done`/`error` per job                               | **unverified** — see below                                                                                                   |
| 4b  | Prefetch is 1 for heavy inference (§2)                                          | `test_main.py::TestProtocolConformance::test_prefetch_is_one`                                                                |
| 4c  | Acks after a terminal outcome, **including on failure** (§5)                    | `test_main.py::TestProtocolConformance::test_acks_after_a_terminal_outcome_including_failure`                                |
|  5  | Echoes `jobId` and, when present, `holdId`                                      | worker unit tests (`test_main.py`)                                                                                           |
| 5b  | **Declares a typed `cause` on every `error` (§3.1)**                            | `test_main.py::TestTypedFailureCause::test_every_error_carries_a_cause`, `::test_the_vocabulary_matches_the_java_contract`   |
| 5c  | **`INPUT_INVALID` is unreachable from a catch-all (§3.1)**                      | `test_main.py::TestTypedFailureCause::test_input_invalid_is_unreachable_from_the_broad_except`                               |
|  6  | Reports measurements only, never units                                          | worker unit tests                                                                                                            |
|  7  | Tolerates redelivery of the same `messageId`                                    | **unverified** for external workers                                                                                          |
|  8  | Binds from `worker.yaml`, not from source literals                              | `test_main.py::test_bindings_come_from_worker_yaml_not_from_source`                                                          |
|  9  | Declares no capability name, and never branches on one                          | `test_main.py::test_worker_yaml_declares_no_capability`, `::test_the_capability_name_does_not_decide`, [PACK-002]/[PACK-003] |
| 10  | Registers and heartbeats without making either a startup dependency             | `test_main.py::test_registration_never_raises_when_the_job_service_is_down`                                                  |
| 11  | Its routing keys are actually drained by some declared worker                   | [EXEC-002]                                                                                                                   |

Every **MUST** in this document appears above, either with a test or marked unverified. That
completeness is itself checked: the acceptance gate enumerates the `MUST` clauses and refuses a
third, silent category — two rows (4b, 4c) were added when it found claims with no row.

**Two requirements are declared unverified rather than quietly assumed** (#4 and #7 for out-of-tree
workers). A protocol nobody verifies is a wish, and a checklist that pretends to cover what it does
not is worse than one that admits the gap. Closing them needs a conformance harness that drives an
arbitrary worker, which is phase H's problem when the first third-party worker arrives.

---

## Related documentation

- [ADR-038](adr/ADR-038-worker-spi-and-registration.md) — this protocol's decision record
- [ADR-037](adr/ADR-037-pack-spi-and-open-core.md) — routing as data; why the AMQP contract is the SPI
- [Pack extensibility architecture](PACK_EXTENSIBILITY_ARCHITECTURE.md) §2.2, §3.3, §3.4
- [Messaging standards](../.agent/rules/messaging_standards.md) · [Governance contract](../AGENTS.md) §6
