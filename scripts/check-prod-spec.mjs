#!/usr/bin/env node
// The pilot's prod app spec stays honest (krizaka-internal#2132, ADR SOV-105): `node scripts/check-prod-spec.mjs`.
// It refuses, in infra/deploy/sovkora-prod.app.yaml: a plain secret value, a development host or resource, the web app,
// the video worker, the GPU relay, a second address, a routed component other than the edge, a region outside Toronto,
// and an edge that would admit a key minted for another environment. Tested by scripts/test/prod-app-spec.test.mjs.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const SPEC = new URL("../infra/deploy/sovkora-prod.app.yaml", import.meta.url);

/** The only components the pilot runs: the engine and its edge (SOV-105 §1). */
const COMPONENTS = ["core", "edge"];
/** The one address of the pilot: server to server, no page. */
const DOMAINS = ["api.sovkora.com"];
/** A secret's value is a placeholder filled at apply, or a reference App Platform fills from the cluster. */
const PLACEHOLDER = /^__[A-Z0-9_]+__$/;
const REFERENCE = /^\$\{[a-z0-9-]+\.[A-Z_]+\}$/;
/** A key whose last word names a secret. */
const SECRET_KEY = /_(PASSWORD|PASS|SECRET|KEY|SALT|TOKEN)$/;
/** A key prefix of ours or of a provider: `oz_`, `sk-`, `dop_v1_`. */
const KEY_PREFIX = /(^|[^A-Za-z0-9])(oz_|sk-|dop_v1_)/;
/** A value that looks like a credential: a known key prefix, or one long opaque run (40 or more key characters). */
const looksLikeCredential = (value) => KEY_PREFIX.test(value) || value.split(/[^A-Za-z0-9+/=_-]/).some((run) => run.length >= 40);
/** A development host, resource or migration location: `dev.`, `-dev`, `db/dev`, `dev` as a word. */
const DEV = /(^|[^a-z])dev([^a-z]|$)/i;
/** What the pilot must never run (SOV-105 §1): the web app, the video worker, the GPU relay and its engines. */
const FORBIDDEN = [
  [/apps\/web|^\s+- name: web$/m, "the web app"],
  [/hosted-video|VIDEO_GENERATION|IMAGE_GENERATION|AI_INFERENCE_KEY_ALL/, "the video worker or an image engine"],
  [/gpu-relay|OLLAMA/, "the GPU relay"],
];

/** One env line of the flow form `- { key: K, scope: S, [type: SECRET,] value: V }`; null for any other line. */
function envOf(line) {
  const m = /^\s+- \{ key: ([A-Z0-9_]+), scope: [A-Z_]+,( type: SECRET,)? value: (.*) \}\s*$/.exec(line);
  if (!m) return null;
  const raw = m[3].trim();
  return { key: m[1], secret: Boolean(m[2]), value: raw.startsWith('"') ? raw.slice(1, -1) : raw };
}

/** The text of a line without its comment (values in this spec never hold a `#`). */
const code = (line) => line.replace(/(^|\s)#.*$/, "");

/** The components (the entries of the top-level `services:` section) and the sections the pilot does not have. */
function componentProblems(body) {
  const problems = [];
  const services = /^services:\n([\s\S]*?)(?=^\S|$(?![\s\S]))/m.exec(body)?.[1] ?? "";
  const components = [...services.matchAll(/^ {2}- name: (\S+)$/gm)].map((m) => m[1]);
  for (const name of components.filter((c) => !COMPONENTS.includes(c))) {
    problems.push(`component ${name} is not part of the pilot (only ${COMPONENTS.join(", ")})`);
  }
  for (const section of ["static_sites", "workers", "jobs", "functions"].filter((sec) => new RegExp(`^${sec}:`, "m").test(body))) {
    problems.push(`a ${section} section is not part of the pilot`);
  }
  for (const [pattern, what] of FORBIDDEN.filter(([pattern]) => pattern.test(body))) {
    problems.push(`${what} is not part of the pilot`);
  }
  return problems;
}

/** Where the pilot answers: the edge alone, on one address, in Toronto. */
function placeProblems(body) {
  const problems = [];
  const routed = [...body.matchAll(/- component:\n\s+name: (\S+)/g)].map((m) => m[1]);
  if (routed.join() !== "edge") problems.push(`only the edge is routed, not: ${routed.join(", ") || "nothing"}`);
  const domains = [...body.matchAll(/- domain: (\S+)/g)].map((m) => m[1]);
  if (domains.join() !== DOMAINS.join()) problems.push(`the pilot has one address, ${DOMAINS.join()}, not: ${domains.join(", ")}`);
  if (!/^region: tor$/m.test(body)) problems.push("the region is Toronto (tor): personal data stays in Canada (SOV-105 §4)");
  return problems;
}

/** No line names a development host or resource; every env is in the one-line form this check reads. */
function lineProblems(lines) {
  const problems = lines.flatMap((line, i) => (DEV.test(code(line)) ? [`line ${i + 1} names a development host or resource: ${line.trim()}`] : []));
  if (lines.some((l) => /^\s+- key: /.test(l))) problems.push("every env is one line, `- { key: …, value: … }`, so this check reads it");
  return problems;
}

/** One env's secret problems: a secret is a placeholder or a reference, and nothing else holds a credential. */
function secretProblems(env) {
  const filled = PLACEHOLDER.test(env.value) || REFERENCE.test(env.value);
  const problems = [];
  if (env.secret && !filled) problems.push(`${env.key} holds a plain secret value: a placeholder or a reference only`);
  if (!env.secret && SECRET_KEY.test(env.key)) problems.push(`${env.key} is not a SECRET`);
  if (!filled && looksLikeCredential(env.value)) problems.push(`${env.key} has a value that looks like a credential`);
  return problems;
}

/** The envs of one component, by key. */
function envsOf(text, component) {
  const part = text.split(/^ {2}- name: /m).find((p) => p.startsWith(`${component}\n`)) ?? "";
  return Object.fromEntries(part.split("\n").map(envOf).filter(Boolean).map((e) => [e.key, e.value]));
}

/** What the components must declare: prod keys only at the edge, prod's spend caps and the one worker in core. */
function declarationProblems(text) {
  const edge = envsOf(text, "edge");
  const core = envsOf(text, "core");
  const problems = [];
  if (edge.AUTH_API_KEY_ENVIRONMENT !== "prod") problems.push("the edge declares AUTH_API_KEY_ENVIRONMENT=prod, so a dev key is refused");
  if (core.AI_SPEND_ENVIRONMENT !== "prod") problems.push("core declares AI_SPEND_ENVIRONMENT=prod, so the spend caps read are prod's");
  // The container's worker list (run-services.sh): core runs the pilot's one worker, the edge none.
  const workers = Object.keys(core).find((key) => key.endsWith("_WORKERS"));
  if (!workers || core[workers] !== "validation") problems.push("core runs the validation worker, the pilot's one worker, and no other");
  if (edge[workers] !== "") problems.push("the edge runs no worker (its worker list is empty)");
  return problems;
}

/** Every problem of the prod spec `text`, one sentence each; empty when the spec is honest. */
export function prodSpecProblems(text) {
  const lines = text.split("\n");
  const body = lines.map(code).join("\n");
  return [
    ...componentProblems(body),
    ...placeProblems(body),
    ...lineProblems(lines),
    ...lines.map(envOf).filter(Boolean).flatMap(secretProblems),
    ...declarationProblems(text),
  ];
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const problems = prodSpecProblems(readFileSync(SPEC, "utf8"));
  for (const p of problems) console.error(`sovkora-prod.app.yaml: ${p}`);
  if (problems.length) process.exit(1);
  console.log("sovkora-prod.app.yaml: honest (no plain secret, no development host, the pilot's components only)");
}
