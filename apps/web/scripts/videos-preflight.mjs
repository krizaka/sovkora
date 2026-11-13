#!/usr/bin/env node
// The preflight of the Download page's videos (orazaka-ai-engine#169; free but for one short per-token chat turn). A recorded chat is a PAID run (a model answers), and
// two of them were spent without proof because a precondition was false (the stream path never offered tools; then the tool was
// not registered on dev). So every paid step is preceded by this check, which calls no model but for one short chat turn (step 5), and proves, in order:
//   1. the persona signs in on the edge API (the credentials work, the engine answers);
//   2. the tool the recorded prompt needs is among the tools the engine offers that persona (GET /api/v1/mcp/tools: the tools the
//      ToolInterceptor attaches to a chat turn), with the argument the prompt gives it;
//   3. the persona signs in on the web (NextAuth through ROUTER_URL: a booting core shows here while the edge may still answer);
//   4. dev is not on an automated rollback (the old image would not have the fixes): read from the DigitalOcean app when a token
//      is given (DIGITALOCEAN_ACCESS_TOKEN), otherwise reported as NOT VERIFIED, and refused with --require-rollback-guard.
//   5. the default chat route is the hosted per-token provider (ADR-095: no GPU is ever left on; dev drifted to the GPU relay's
//      ollama on 2026-11-12), read from the model catalogue, and one short chat turn as the persona, through the product's own
//      routing, returns a non-empty answer (the cost of one short turn, a few tokens);
//   6. the tool-call path is proved free: conversation-service's ToolCallStreamPathTest (a streamed turn through the real engine,
//      the seeded pipeline with RAG, the real tool loop and the real SSE relay, ending in a `tool-call` event) is green on main,
//      and dev's core was deployed after the commit that last changed it (read from the DigitalOcean app; with
//      --require-tool-proof an unreadable deploy time refuses).
//
//   ORAZAKA_USER_EMAIL=… ORAZAKA_USER_PASSWORD=… node scripts/videos-preflight.mjs [--base https://dev.sovkora.com] [--require-rollback-guard]
//
// Exits 1 on the first failed precondition's report (all checks are still listed). Nothing secret is printed.
import { PROOF_TOOL } from "./tour/eric.mjs";

/** The one provider dev chats on (ADR-095, rule 12: chat is a route, not a GPU). */
export const CHAT_PROVIDER = "do-inference";
/** The one short turn of the preflight: a few tokens on the per-token route, no tool, no document. */
export const CHAT_PROBE_PROMPT = "Reply with one short sentence: hello.";

const DO_API = "https://api.digitalocean.com/v2";
const APP_NAME = "orazaka-dev";

/**
 * The tool list of the engine for the persona: is the proof tool there, with a required string `query`?
 * @param {unknown} tools body of GET /api/v1/mcp/tools
 * @param {string} name the tool the recorded prompt needs
 * @returns {{ ok: boolean, message: string }}
 */
export function toolVerdict(tools, name) {
  if (!Array.isArray(tools)) return { ok: false, message: "the engine's tool list is not an array" };
  const names = tools.map((t) => t?.name).filter(Boolean);
  const tool = tools.find((t) => t?.name === name);
  if (!tool)
    return {
      ok: false,
      message: `the tool ${name} is not offered to the persona (offered: ${names.join(", ") || "none"})`,
    };
  let schema = tool.inputSchema;
  try {
    if (typeof schema === "string") schema = JSON.parse(schema);
  } catch {
    return { ok: false, message: `the tool ${name} has an unreadable input schema` };
  }
  if (!schema?.required?.includes?.("query") || schema?.properties?.query?.type !== "string") {
    return {
      ok: false,
      message: `the tool ${name} does not take a required string \`query\` (the recorded prompt gives it one)`,
    };
  }
  return { ok: true, message: `the tool ${name} is offered (offered: ${names.join(", ")})` };
}

/**
 * The drift check (ADR-095): the default chat model of the catalogue must be on the hosted per-token provider. Dev drifted to
 * the baseline's `tinyllama:latest` on `ollama` (the GPU relay, nothing behind it) when its routing rows were not re-applied.
 * @param {unknown} catalog body of GET /api/v1/models/catalog
 * @returns {{ ok: boolean, message: string, provider: string | null }}
 */
export function chatRouteVerdict(catalog) {
  if (!Array.isArray(catalog)) {
    return { ok: false, message: "the model catalogue is not an array", provider: null };
  }
  const chat = catalog.find((m) => m?.category === "chat" && m?.isDefault === true);
  if (!chat) {
    return { ok: false, message: "the catalogue has no default chat model", provider: null };
  }
  const route = `${chat.modelName} on ${chat.providerName}`;
  if (chat.providerName !== CHAT_PROVIDER) {
    return {
      ok: false,
      message: `the default chat route is ${route}, not the hosted per-token provider ${CHAT_PROVIDER} (ADR-095): dev drifted from infra/deploy/dev-data.sql`,
      provider: chat.providerName ?? null,
    };
  }
  return { ok: true, message: `the default chat route is ${route}`, provider: chat.providerName };
}

/** The text of one data line: the `content` (or `text`) of its JSON, else the raw line. */
function dataText(line) {
  const raw = line.slice(5).trim();
  try {
    const parsed = JSON.parse(raw);
    const chunk = parsed?.content ?? parsed?.text;
    return typeof chunk === "string" ? chunk : "";
  } catch {
    return raw;
  }
}

/** The text of one SSE block; the pipeline, routing, tool-call and error events carry none. */
function blockText(block) {
  const lines = block.split(/\r?\n/);
  const event =
    lines
      .find((l) => l.startsWith("event:"))
      ?.slice(6)
      .trim() ?? "";
  if (/^(pipeline-|routing|tool-call|error)/.test(event)) {
    return "";
  }
  return lines
    .filter((l) => l.startsWith("data:"))
    .map(dataText)
    .join("");
}

/** The text a chat turn streamed (its event stream, as the browser reads it). */
export function chatTurnText(body) {
  return body
    .split(/\r?\n\r?\n/)
    .map(blockText)
    .join("");
}

/** One short turn must come back with an answer; the failure names the route it went through. @returns {{ ok: boolean, message: string }} */
export function chatAnswerVerdict(answer, provider) {
  if (answer.trim().length === 0) {
    return { ok: false, message: `the chat turn on the route ${provider ?? "unknown"} gave no answer` };
  }
  return {
    ok: true,
    message: `the chat answers on the route ${provider ?? "unknown"} (${answer.trim().length} characters)`,
  };
}

/** The persona's API sign-in. @returns {{ ok: boolean, message: string }} */
export function loginVerdict(status, hasToken) {
  if (status !== 200)
    return { ok: false, message: `the persona sign-in on the edge answered HTTP ${status} (expected 200)` };
  if (!hasToken) return { ok: false, message: "the persona sign-in on the edge answered 200 without a token" };
  return { ok: true, message: "the persona signs in on the edge" };
}

/** The persona's web sign-in (the same check as orazaka's dev-persona-smoke). @returns {{ ok: boolean, message: string }} */
export function webVerdict(status, session, email) {
  if (status !== 200) return { ok: false, message: `the web sign-in answered HTTP ${status} (expected 200)` };
  if (session?.user?.email?.toLowerCase() !== email.toLowerCase())
    return { ok: false, message: "the web sign-in gave no session for the persona (the web cannot reach the router?)" };
  return { ok: true, message: "the persona signs in on the web" };
}

/**
 * dev must not be an automated rollback (2026-10-29: it ran an old image for hours after two failed deployments). Same rule as
 * orazaka's `rollbackVerdict` (scripts/dev-redeploy.mjs --check), on the deployments newest first.
 * @param {{ id: string, created_at: string, cause: string, phase: string }[] | null} deployments null = could not be read
 * @param {boolean} required refuse when it could not be read
 * @returns {{ ok: boolean, message: string }}
 */
export function rollbackVerdict(deployments, required) {
  if (!deployments)
    return {
      ok: !required,
      message: `rollback guard NOT VERIFIED (no DigitalOcean access)${required ? ": refused" : ""}`,
    };
  const active = deployments.find((d) => d.phase === "ACTIVE");
  if (!active) return { ok: false, message: "no ACTIVE deployment" };
  if (/rollback/i.test(active.cause))
    return { ok: false, message: `dev runs an automated rollback (${active.id.slice(0, 8)})` };
  const failedAfter = deployments.find(
    (d) => d.phase === "ERROR" && Date.parse(d.created_at) > Date.parse(active.created_at),
  );
  if (failedAfter)
    return {
      ok: false,
      message: `deployment ${failedAfter.id.slice(0, 8)} failed after the active one (${active.id.slice(0, 8)})`,
    };
  return { ok: true, message: `the active deployment ${active.id.slice(0, 8)} is not a rollback` };
}

// One repository since ADR-029: the proof lives in conversation-service's folder of this repository, and its CI is ci.yml.
const PROOF_REPO = "krizaka/sovkora";
export const PROOF_TEST =
  "services/conversation/sovkora-conversation-service/src/test/java/com/krizaka/sovkora/conversation/infrastructure/adapter/rest/ToolCallStreamPathTest.java";

// ci.yml's other jobs (the Web app one holds the 14-day app-videos guard, i.e. the recordings this workflow republishes)
// must not decide the proof: only the job that runs ToolCallStreamPathTest does.
export const PROOF_JOB = "Java reactor";

/** The conclusion of the proof's job in the payload of `actions/runs/{id}/jobs`; null when it is not there. */
export function proofJobConclusion(payload) {
  return payload?.jobs?.find((j) => j.name === PROOF_JOB)?.conclusion ?? null;
}

// How many recent completed main runs of ci.yml are searched for one where the proof's job really ran (path filters skip it).
export const PROOF_RUNS_PAGE_SIZE = 20;

/**
 * The newest run, in the page (newest first), where the proof's job concluded success or failure; skipped, cancelled or
 * missing never count. Null when no run of the page ran it.
 * @param {{ id: number }[]} runs
 * @param {(id: number) => Promise<object | null>} getJobs the payload of `actions/runs/{id}/jobs`
 * @returns {Promise<{ runId: number, conclusion: "success" | "failure" } | null>}
 */
export async function lastRunWhereProofRan(runs, getJobs) {
  for (const { id } of runs) {
    const conclusion = proofJobConclusion(await getJobs(id));
    if (conclusion === "success" || conclusion === "failure") {
      return { runId: id, conclusion };
    }
  }
  return null;
}

/**
 * The free proof that a streamed turn emits a `tool-call` event (orazaka-ai-engine#169): three paid runs came back without one,
 * so no paid run starts until this test is green on main and dev runs a build made after it.
 * @param {{ ciConclusion: string | null, ciRunId?: number | null, proofCommittedAt: string | null, coreDeployedAt: string | null }} facts
 * @param {boolean} required refuse when the deploy time could not be read
 * @returns {{ ok: boolean, message: string }}
 */
export function toolPathVerdict({ ciConclusion, ciRunId = null, proofCommittedAt, coreDeployedAt }, required) {
  if (!proofCommittedAt) {
    return {
      ok: false,
      message: "the tool-call proof (ToolCallStreamPathTest) is not on main of conversation-service",
    };
  }
  if (ciConclusion === null) {
    return {
      ok: false,
      message: `no recent main run of ci.yml ran the ${PROOF_JOB} job (or the runs are unreadable): the tool-call proof is not green`,
    };
  }
  if (ciConclusion !== "success") {
    return {
      ok: false,
      message: `the ${PROOF_JOB} CI job on main is ${ciConclusion} (run ${ciRunId}), not success: the tool-call proof is not green`,
    };
  }
  if (!coreDeployedAt) {
    return {
      ok: !required,
      message: `tool-call proof green on main (run ${ciRunId}); the build dev runs NOT VERIFIED (no DigitalOcean access)${required ? ": refused" : ""}`,
    };
  }
  if (Date.parse(coreDeployedAt) < Date.parse(proofCommittedAt)) {
    return {
      ok: false,
      message: `dev's core was deployed (${coreDeployedAt}) before the tool-call proof (${proofCommittedAt}): redeploy first`,
    };
  }
  return {
    ok: true,
    message: `the tool-call proof is green on main (${PROOF_JOB}, run ${ciRunId}) and dev runs a build made after it`,
  };
}

/** What the GitHub API says about the proof; null fields when it cannot be read. */
async function toolPathFacts(coreDeployedAt) {
  const get = async (path) => {
    try {
      const res = await fetch(`https://api.github.com/repos/${PROOF_REPO}/${path}`, {
        headers: { Accept: "application/vnd.github+json" },
      });
      return res.ok ? await res.json() : null;
    } catch {
      return null;
    }
  };
  const commits = await get(`commits?sha=main&per_page=1&path=${encodeURIComponent(PROOF_TEST)}`);
  const runs = await get(
    `actions/workflows/ci.yml/runs?branch=main&event=push&status=completed&per_page=${PROOF_RUNS_PAGE_SIZE}`,
  );
  const ran = await lastRunWhereProofRan(runs?.workflow_runs ?? [], (id) =>
    get(`actions/runs/${id}/jobs?per_page=100`),
  );
  return {
    proofCommittedAt: commits?.[0]?.commit?.committer?.date ?? null,
    ciConclusion: ran?.conclusion ?? null,
    ciRunId: ran?.runId ?? null,
    coreDeployedAt,
  };
}

/**
 * @param {Record<string, { ok: boolean, message: string }>} checks in the order they ran
 * @returns {{ ok: boolean, lines: string[] }}
 */
export function report(checks) {
  const entries = Object.entries(checks);
  return {
    ok: entries.every(([, v]) => v.ok),
    lines: entries.map(([k, v]) => `${v.ok ? "ok  " : "FAIL"} ${k}: ${v.message}`),
  };
}

/** Signs in the way the browser does (csrf, credentials callback, session), without a browser. */
async function webSignIn(web, email, pass) {
  const jar = new Map();
  const send = async (path, init = {}) => {
    const cookie = [...jar].map(([k, v]) => `${k}=${v}`).join("; ");
    const res = await fetch(`${web}${path}`, { ...init, redirect: "manual", headers: { ...init.headers, cookie } });
    for (const c of res.headers.getSetCookie()) {
      const kv = c.split(";")[0];
      const i = kv.indexOf("=");
      jar.set(kv.slice(0, i), kv.slice(i + 1));
    }
    return res;
  };
  const { csrfToken } = await (await send("/api/auth/csrf")).json();
  const cb = await send("/api/auth/callback/credentials", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ csrfToken, email, password: pass, json: "true" }),
  });
  const session = await (await send("/api/auth/session")).json().catch(() => null);
  return webVerdict(cb.status, session, email);
}

/** Posts to the edge and returns the response, or throws naming the route and the HTTP status. */
async function edgePost(url, headers, payload, provider, timeoutMs) {
  const res = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(timeoutMs),
  });
  return res.ok ? res : Promise.reject(new Error(`answered HTTP ${res.status} on the route ${provider}`));
}

/** One short chat turn as the persona on the product's own route (no model named): the answer must not be empty. */
async function chatTurn(base, auth, provider) {
  const headers = { ...auth, "Content-Type": "application/json" };
  const conversationId = crypto.randomUUID();
  try {
    await edgePost(
      `${base}/edge/api/v1/chat/sessions`,
      headers,
      { conversationId, title: "preflight" },
      provider,
      15_000,
    );
    const res = await edgePost(
      `${base}/edge/api/v1/chat/stream/${conversationId}`,
      headers,
      { prompt: CHAT_PROBE_PROMPT, assetIds: [] },
      provider,
      90_000,
    );
    return chatAnswerVerdict(chatTurnText(await res.text()), provider);
  } catch (e) {
    return { ok: false, message: `the chat turn failed: ${e.message}` };
  }
}

/** The deployments of the dev app, newest first, or null when DigitalOcean cannot be read. */
async function deployments(token) {
  if (!token) return null;
  const headers = { Authorization: `Bearer ${token}` };
  try {
    const apps = await (await fetch(`${DO_API}/apps?per_page=200`, { headers })).json();
    const id = apps.apps?.find((a) => a.spec?.name === APP_NAME)?.id;
    if (!id) return null;
    const list = await (await fetch(`${DO_API}/apps/${id}/deployments?per_page=10`, { headers })).json();
    return (list.deployments ?? []).map((d) => ({
      id: d.id,
      created_at: d.created_at,
      cause: d.cause ?? "",
      phase: d.phase,
    }));
  } catch {
    return null;
  }
}

async function main(args) {
  const at = args.indexOf("--base");
  const base = (at >= 0 && args[at + 1] ? args[at + 1] : "https://dev.sovkora.com").replace(/\/$/, "");
  const email = process.env.ORAZAKA_USER_EMAIL;
  const pass = process.env.ORAZAKA_USER_PASSWORD;
  if (!email || !pass) throw new Error("ORAZAKA_USER_EMAIL and ORAZAKA_USER_PASSWORD are required (the demo persona)");
  const checks = {};
  const res = await fetch(`${base}/edge/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password: pass }),
  });
  const body = res.ok ? await res.json().catch(() => ({})) : {};
  checks["edge sign-in"] = loginVerdict(res.status, Boolean(body.token));
  if (body.token) {
    const list = await fetch(`${base}/edge/api/v1/mcp/tools`, { headers: { Authorization: `Bearer ${body.token}` } });
    checks[`tool ${PROOF_TOOL}`] = list.ok
      ? toolVerdict(await list.json().catch(() => null), PROOF_TOOL)
      : { ok: false, message: `the tool list answered HTTP ${list.status}` };
  } else {
    checks[`tool ${PROOF_TOOL}`] = { ok: false, message: "not checked (no sign-in)" };
  }
  checks["chat route"] = { ok: false, message: "not checked (no sign-in)" };
  checks["chat answer"] = { ok: false, message: "not checked (no sign-in)" };
  if (body.token) {
    const auth = { Authorization: `Bearer ${body.token}` };
    const catalog = await fetch(`${base}/edge/api/v1/models/catalog`, { headers: auth })
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null);
    const route = chatRouteVerdict(catalog);
    checks["chat route"] = route;
    checks["chat answer"] = route.ok
      ? await chatTurn(base, auth, route.provider)
      : { ok: false, message: "not checked (the default chat route is not the hosted per-token provider)" };
  }
  checks["web sign-in"] = await webSignIn(base, email, pass).catch((e) => ({
    ok: false,
    message: `the web sign-in failed: ${e.message}`,
  }));
  const deployed = await deployments(process.env.DIGITALOCEAN_ACCESS_TOKEN);
  checks["rollback guard"] = rollbackVerdict(deployed, args.includes("--require-rollback-guard"));
  const activeAt = deployed?.find((d) => d.phase === "ACTIVE")?.created_at ?? null;
  checks["tool-call proof"] = toolPathVerdict(await toolPathFacts(activeAt), args.includes("--require-tool-proof"));
  const r = report(checks);
  process.stdout.write(
    `${r.lines.join("\n")}\n${r.ok ? "preflight green: a paid run may start" : "preflight RED: no paid step runs"}\n`,
  );
  if (!r.ok) process.exitCode = 1;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main(process.argv.slice(2)).catch((e) => {
    process.stderr.write(`preflight RED: ${e.message}\n`);
    process.exitCode = 1;
  });
}
