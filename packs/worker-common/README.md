# worker-common

Code every pack worker shares, so it exists once (org rule: no copy-pasted code).

- `worker_broker.py` — how a worker reaches the RabbitMQ broker: host, credentials and, when the deployment asks, TLS.
- `worker_loop.py` — the job loop: declare the queue and its dead-letter queue, consume, run the worker's handler, publish `done` or `error` (de-duplicated by message id). New workers bring a handler and a `worker.yaml`; older ones still carry their own copy.
- `worker_failure.py` — the one safe answer to a failed job (krizaka-internal#3376). An exception's message can quote the document it was reading, so a failure leaves a worker as a **coded reason** from the worker's closed set (`InvalidJobPayload`, a subclass declaring `REASONS`; cause `INPUT_INVALID`) or the exception's **type name** (cause `EXECUTOR_FAULT`), in the event and in the log: `failure_answer` and `failure_log`. A free-text reason is refused when the exception is built. `packs/tests/worker-failure-no-text.test.mjs` fails on any worker that puts an exception or a slice of input into an event or a log.

A worker imports it through a three-line `broker.py` beside its code (`from worker_broker import *`) that puts this folder on
`sys.path`; its tests and its `worker.yaml` are unchanged. The folder travels with the repository: a deployment that copies a
single pack directory must copy `worker-common/` beside it.
