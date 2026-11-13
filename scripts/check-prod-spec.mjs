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
/** A value that looks like a credential: an `oz_` key, a provider key, or a long opaque string. */
const CREDENTIAL = /(^|[^A-Za-z0-9])(oz_|sk-|dop_v1_)|[A-Za-z0-9+/=_-]{40,}/;
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

/** Every problem of the prod spec `text`, one sentence each; empty when the spec is honest. */
export function prodSpecProblems(text) {
  const problems = [];
  const lines = text.split("\n");
  const body = lines.map(code).join("\n");

  // The components are the entries of the top-level `services:` section (the databases are references, not components).
  const services = /^services:\n([\s\S]*?)(?=^\S|$(?![\s\S]))/m.exec(body)?.[1] ?? "";
  const components = [...services.matchAll(/^ {2}- name: (\S+)$/gm)].map((m) => m[1]);
  for (const name of components) {
    if (!COMPONENTS.includes(name)) problems.push(`component ${name} is not part of the pilot (only ${COMPONENTS.join(", ")})`);
  }
  for (const section of ["static_sites", "workers", "jobs", "functions"]) {
    if (new RegExp(`^${section}:`, "m").test(body)) problems.push(`a ${section} section is not part of the pilot`);
  }
  const routed = [...body.matchAll(/- component:\n\s+name: (\S+)/g)].map((m) => m[1]);
  if (routed.join() !== "edge") problems.push(`only the edge is routed, not: ${routed.join(", ") || "nothing"}`);
  const domains = [...body.matchAll(/- domain: (\S+)/g)].map((m) => m[1]);
  if (domains.join() !== DOMAINS.join()) problems.push(`the pilot has one address, ${DOMAINS.join()}, not: ${domains.join(", ")}`);
  if (!/^region: tor$/m.test(body)) problems.push("the region is Toronto (tor): personal data stays in Canada (SOV-105 §4)");

  for (const [pattern, what] of FORBIDDEN) {
    if (pattern.test(body)) problems.push(`${what} is not part of the pilot`);
  }
  lines.forEach((line, i) => {
    if (DEV.test(code(line))) problems.push(`line ${i + 1} names a development host or resource: ${line.trim()}`);
  });

  const envs = lines.map(envOf).filter(Boolean);
  if (lines.some((l) => /^\s+- key: /.test(l))) problems.push("every env is one line, `- { key: …, value: … }`, so this check reads it");
  for (const env of envs) {
    const placeholder = PLACEHOLDER.test(env.value);
    const reference = REFERENCE.test(env.value);
    if (env.secret && !placeholder && !reference) problems.push(`${env.key} holds a plain secret value: a placeholder or a reference only`);
    if (!env.secret && SECRET_KEY.test(env.key)) problems.push(`${env.key} is not a SECRET`);
    if (!placeholder && !reference && CREDENTIAL.test(env.value)) problems.push(`${env.key} has a value that looks like a credential`);
  }

  const edge = text.split(/^ {2}- name: /m).find((part) => part.startsWith("edge\n")) ?? "";
  const edgeEnv = Object.fromEntries(edge.split("\n").map(envOf).filter(Boolean).map((e) => [e.key, e.value]));
  if (edgeEnv.AUTH_API_KEY_ENVIRONMENT !== "prod") problems.push("the edge declares AUTH_API_KEY_ENVIRONMENT=prod, so a dev key is refused");
  const core = text.split(/^ {2}- name: /m).find((part) => part.startsWith("core\n")) ?? "";
  const coreEnv = Object.fromEntries(core.split("\n").map(envOf).filter(Boolean).map((e) => [e.key, e.value]));
  if (coreEnv.AI_SPEND_ENVIRONMENT !== "prod") problems.push("core declares AI_SPEND_ENVIRONMENT=prod, so the spend caps read are prod's");
  // The container's worker list (run-services.sh): core runs the pilot's one worker, the edge none.
  const workers = Object.keys(coreEnv).find((key) => key.endsWith("_WORKERS"));
  if (!workers || coreEnv[workers] !== "validation") problems.push("core runs the validation worker, the pilot's one worker, and no other");
  if (edgeEnv[workers] !== "") problems.push("the edge runs no worker (its worker list is empty)");
  return problems;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const problems = prodSpecProblems(readFileSync(SPEC, "utf8"));
  for (const p of problems) console.error(`sovkora-prod.app.yaml: ${p}`);
  if (problems.length) process.exit(1);
  console.log("sovkora-prod.app.yaml: honest (no plain secret, no development host, the pilot's components only)");
}
