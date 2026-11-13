// One AMQP loop (krizaka-internal#3397): the job loop (exchange and queue declaration, dead-lettering, de-duplication,
// the done / error events) lives in packs/worker-common/worker_loop.py, and a pack worker runs on `worker_loop.run`. A pack
// worker that declares an exchange or consumes a queue itself is a copy that drifts (the failure answer of #3376 had to be
// fixed in five of them). echo-toolkit is the reference worker that deliberately links nothing.
// COPIES lists the workers still carrying their own loop, each to be moved onto worker_loop.run; a moved worker must leave
// the list (a stale entry fails), so it can only shrink.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { relative } from "node:path";
import { test } from "node:test";
import { PACKS, packWorkerFiles } from "./pack-worker-files.mjs";

const BROKER_CODE = /\b(basic_consume|exchange_declare)\b/;
const REFERENCE_WORKERS = new Set(["echo-toolkit"]);

/** `pack/worker/file.py` of the workers still carrying their own loop. */
export const COPIES = [
  "document-validation/worker/validation_worker.py",
  "message-authenticity/worker/authenticity_worker.py",
  "profile-photo-check/worker/photo_worker.py",
  "tenant-screening-qc/worker/screening_worker.py",
];

/** `pack/worker/file.py` of every pack worker file (the reference worker apart) that declares or consumes on the broker. */
const carriers = () =>
  packWorkerFiles(REFERENCE_WORKERS)
    .filter((f) => BROKER_CODE.test(readFileSync(f, "utf8")))
    .map((f) => relative(PACKS, f));

test("a pack worker has no AMQP loop of its own, but the listed copies", () => {
  const unexpected = carriers().filter((f) => !COPIES.includes(f));
  const advice = "declare nothing on the broker here: run on worker-common/worker_loop.run";
  assert.deepEqual(unexpected, [], `${advice}\n${unexpected.join("\n")}`);
});

test("every listed copy still carries broker code (a moved worker leaves the list)", () => {
  const found = new Set(carriers());
  const stale = COPIES.filter((f) => !found.has(f));
  assert.deepEqual(stale, [], `no longer carries a loop, remove from COPIES:\n${stale.join("\n")}`);
});
