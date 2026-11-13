// A failed job leaves a worker as a coded reason or an exception TYPE NAME, never as text (krizaka-internal#3376): an
// exception's message can quote the document it was reading, the saga stores the event and the log outlives the job.
// This sweep reads every pack worker's source (and the media worker's consumer and speech server) and fails on:
//   - an exception variable (`except ... as bad`) reaching `emit(...)`, `_emit(...)`, `.tell(...)`, `send_job_error(...)`,
//     `_send(...)`, `print(...)` or a logger call, other than as `type(bad).__name__` or `bad.reason`;
//   - a slice of some value (`raw[:120]`) inside one of those calls;
//   - a traceback printer (`exc_info`, `.exception(`, `print_exc`, `format_exc`);
//   - an `InvalidJobPayload(...)` built from anything but a bare CODE constant (a free text is a leak waiting for a value).
// ALLOWED lists the sites still tolerated, as `file:rule`; each must still be hit (a fixed site must leave the list), so it
// can only shrink.
import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";
import { test } from "node:test";

const PACKS = new URL("..", import.meta.url).pathname;
const REPO = join(PACKS, "..");

/** `file:rule` pairs still tolerated, each with its reason. */
export const ALLOWED = [
  // The image proxy relays the image engine's own HTTP answer (its status and body) to the caller that asked, as it came:
  // that is not an exception of this worker, and it reaches neither an event nor a log.
  "workers/media/app/speech_server.py:exception-in-output",
];

function workerSources() {
  const files = [];
  for (const entry of readdirSync(PACKS, { withFileTypes: true })) {
    const dir = join(PACKS, entry.name, "worker");
    if (!entry.isDirectory() || !existsSync(dir)) continue;
    for (const f of readdirSync(dir)) if (f.endsWith(".py") && !f.startsWith("test_")) files.push(join(dir, f));
  }
  files.push(join(PACKS, "worker-common", "worker_loop.py"), join(PACKS, "worker-common", "worker_failure.py"));
  for (const f of ["consumer.py", "speech_server.py"]) files.push(join(REPO, "workers", "media", "app", f));
  return files.filter((f) => existsSync(f));
}

const EMITTERS =
  /(?<![\w.])(?:self\.)?(?:print|emit|_emit|tell|send_job_error|_send)\(|\b(?:log|logger|logging)\.\w+\(/g;

/** The text of the call whose opening parenthesis is at `open`, quotes and nesting respected. */
function callText(source, open) {
  let depth = 0;
  let quote = null;
  for (let i = open; i < source.length; i++) {
    const c = source[i];
    if (quote) {
      if (c === "\\") i++;
      else if (c === quote) quote = null;
    } else if (c === '"' || c === "'") quote = c;
    else if (c === "(") depth++;
    else if (c === ")" && --depth === 0) return source.slice(open, i + 1);
  }
  return source.slice(open);
}

/** `args` without the calls to worker-common's own answer helpers: they are what makes a failure safe to emit. */
function withoutSafeHelpers(args) {
  let rest = args;
  for (;;) {
    const m = /(?:\w+\.)?failure_(?:log|answer)\(/.exec(rest);
    if (!m) return rest;
    rest =
      rest.slice(0, m.index) + rest.slice(m.index + m[0].length - 1 + callText(rest, m.index + m[0].length - 1).length);
  }
}

const lineOf = (source, index) => source.slice(0, index).split("\n").length;

export function findings(source) {
  const out = [];
  const names = [...source.matchAll(/\bexcept\b[^\n:]*\bas\s+(\w+)/g)].map((m) => m[1]);
  for (const m of source.matchAll(EMITTERS)) {
    const open = m.index + m[0].length - 1;
    const args = callText(source, open);
    const line = lineOf(source, m.index);
    const safe = withoutSafeHelpers(args);
    for (const name of new Set(names)) {
      const bare = safe.replaceAll(`type(${name}).__name__`, "").replaceAll(`${name}.reason`, "");
      // The name used as a value: inside an f-string brace, as an argument, or with an attribute — not as a word of a message.
      const asValue = new RegExp(
        `\\bf(["'])(?:(?!\\1).)*\\{[^}]*\\b${name}\\b|[(,]\\s*${name}\\s*[,)]|\\b${name}\\.\\w`,
      );
      if (asValue.test(bare)) out.push({ rule: "exception-in-output", line });
    }
    if (/[\w)\]]\[[^\][]*:[^\][]*\]/.test(args)) out.push({ rule: "slice-in-output", line });
  }
  for (const m of source.matchAll(/exc_info|\.exception\(|print_exc|format_exc/g))
    out.push({ rule: "traceback", line: lineOf(source, m.index) });
  for (const m of source.matchAll(/InvalidJobPayload\(/g)) {
    const lineStart = source.lastIndexOf("\n", m.index) + 1;
    if (/^\s*class\s/.test(source.slice(lineStart, m.index))) continue;
    const args = callText(source, m.index + m[0].length - 1);
    if (!/^\(\s*[A-Z][A-Z0-9_]*\s*\)$/.test(args))
      out.push({ rule: "free-text-reason", line: lineOf(source, m.index) });
  }
  return out;
}

test("the sweep sees the pack workers", () => {
  const names = workerSources().map((f) => relative(REPO, f));
  for (const expected of [
    "packs/echo-toolkit/worker/echo_worker.py",
    "packs/profile-photo-check/worker/photo_worker.py",
    "packs/worker-common/worker_loop.py",
    "workers/media/app/consumer.py",
  ])
    assert.ok(names.includes(expected), `${expected} is not swept: ${names.join(", ")}`);
});

test("the sweep catches what it is written for", () => {
  assert.deepEqual(
    findings('try:\n  x()\nexcept ValueError as bad:\n  _emit(c, j, "error", {"error": str(bad)})\n').map(
      (f) => f.rule,
    ),
    ["exception-in-output"],
  );
  assert.deepEqual(
    findings('print(f"seen {raw[:120]!r}")').map((f) => f.rule),
    ["slice-in-output"],
  );
  assert.deepEqual(
    findings('raise InvalidJobPayload(f"bad {x}")').map((f) => f.rule),
    ["free-text-reason"],
  );
  assert.deepEqual(
    findings('except Exception as failure:\n  _emit(c, j, "error", {"error": type(failure).__name__})\n'),
    [],
  );
  assert.deepEqual(findings("raise InvalidJobPayload(FIELD_NOT_JSON) from bad"), []);
});

test("no pack worker carries exception text or input into an event or a log", () => {
  const hits = new Map();
  for (const file of workerSources()) {
    for (const f of findings(readFileSync(file, "utf8"))) {
      const key = `${relative(REPO, file)}:${f.rule}`;
      hits.set(key, [...(hits.get(key) ?? []), f.line]);
    }
  }
  const unexpected = [...hits].filter(([key]) => !ALLOWED.includes(key));
  assert.deepEqual(
    unexpected.map(([key, lines]) => `${key} (line ${lines.join(", ")})`),
    [],
    "a worker failure is a coded reason or a type name: use worker_failure.failure_answer / failure_log",
  );
  const stale = ALLOWED.filter((key) => !hits.has(key));
  assert.deepEqual(stale, [], "an allowed site that no longer exists must leave ALLOWED (it can only shrink)");
});
