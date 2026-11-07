#!/usr/bin/env node
// The free preflight of the Download page's videos (orazaka-ai-engine#169). A recorded chat is a PAID run (a model answers), and
// two of them were spent without proof because a precondition was false (the stream path never offered tools; then the tool was
// not registered on dev). So every paid step is preceded by this check, which calls no model and proves, in order:
//   1. the persona signs in on the edge API (the credentials work, the engine answers);
//   2. the tool the recorded prompt needs is among the tools the engine offers that persona (GET /api/v1/mcp/tools: the tools the
//      ToolInterceptor attaches to a chat turn), with the argument the prompt gives it;
//   3. the persona signs in on the web (NextAuth through ROUTER_URL: a booting core shows here while the edge may still answer);
//   4. dev is not on an automated rollback (the old image would not have the fixes): read from the DigitalOcean app when a token
//      is given (DIGITALOCEAN_ACCESS_TOKEN), otherwise reported as NOT VERIFIED, and refused with --require-rollback-guard.
//   5. the tool-call path is proved free: conversation-service's ToolCallStreamPathTest (a streamed turn through the real engine,
//      the seeded pipeline with RAG, the real tool loop and the real SSE relay, ending in a `tool-call` event) is green on main,
//      and dev's core was deployed after the commit that last changed it (read from the DigitalOcean app; with
//      --require-tool-proof an unreadable deploy time refuses).
//
//   ORAZAKA_USER_EMAIL=… ORAZAKA_USER_PASSWORD=… node scripts/videos-preflight.mjs [--base https://www.orazaka.com] [--require-rollback-guard]
//
// Exits 1 on the first failed precondition's report (all checks are still listed). Nothing secret is printed.
import { PROOF_TOOL } from "./tour/eric.mjs";

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

const PROOF_REPO = "krizaka/orazaka-conversation-service";
const PROOF_TEST =
  "src/test/java/com/krizaka/orazaka/conversationservice/infrastructure/adapter/rest/ToolCallStreamPathTest.java";

/**
 * The free proof that a streamed turn emits a `tool-call` event (orazaka-ai-engine#169): three paid runs came back without one,
 * so no paid run starts until this test is green on main and dev runs a build made after it.
 * @param {{ ciConclusion: string | null, proofCommittedAt: string | null, coreDeployedAt: string | null }} facts
 * @param {boolean} required refuse when the deploy time could not be read
 * @returns {{ ok: boolean, message: string }}
 */
export function toolPathVerdict({ ciConclusion, proofCommittedAt, coreDeployedAt }, required) {
  if (!proofCommittedAt) {
    return {
      ok: false,
      message: "the tool-call proof (ToolCallStreamPathTest) is not on main of conversation-service",
    };
  }
  if (ciConclusion !== "success") {
    return {
      ok: false,
      message: `conversation-service CI on main is ${ciConclusion ?? "unreadable"}, not success: the tool-call proof is not green`,
    };
  }
  if (!coreDeployedAt) {
    return {
      ok: !required,
      message: `tool-call proof green on main; the build dev runs NOT VERIFIED (no DigitalOcean access)${required ? ": refused" : ""}`,
    };
  }
  if (Date.parse(coreDeployedAt) < Date.parse(proofCommittedAt)) {
    return {
      ok: false,
      message: `dev's core was deployed (${coreDeployedAt}) before the tool-call proof (${proofCommittedAt}): redeploy first`,
    };
  }
  return { ok: true, message: "the tool-call proof is green on main and dev runs a build made after it" };
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
  const runs = await get("actions/runs?branch=main&event=push&status=completed&per_page=1");
  return {
    proofCommittedAt: commits?.[0]?.commit?.committer?.date ?? null,
    ciConclusion: runs?.workflow_runs?.[0]?.conclusion ?? null,
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
  const base = (at >= 0 && args[at + 1] ? args[at + 1] : "https://www.orazaka.com").replace(/\/$/, "");
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
