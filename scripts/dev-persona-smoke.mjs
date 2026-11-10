#!/usr/bin/env node
/**
 * Persona sign-in smoke: the demo persona (Éric) is the face of every recording, so after each dev deploy (and every
 * 30 minutes, .github/workflows/dev-persona-smoke.yml) he must be able to sign in and read his profile.
 *
 *   node scripts/dev-persona-smoke.mjs [eric|mira]
 *
 * Credentials: env APP_QA_ORAZAKA_<PERSONA>_EMAIL / _PASSWORD (repository secrets in CI) or, on a Mac, @dev through
 * secret.sh. Never printed. Exits 1 with the HTTP status when the sign-in fails (the 2026-10-29 bug: two accounts with
 * the e-mail, login answered 500).
 */
import { execFileSync } from "node:child_process";
import { homedir } from "node:os";
import { join } from "node:path";

const WEB = process.env.ORAZAKA_WEB_URL ?? "https://dev.sovkora.com";
const EDGE = process.env.ORAZAKA_EDGE_URL ?? "https://dev.sovkora.com/edge";
const SECRETS = { eric: "PERSONA", mira: "MIRA" };

/** @returns {{ ok: boolean, message: string }} */
export function verdict(loginStatus, hasToken) {
  if (loginStatus !== 200) return { ok: false, message: `the persona sign-in answered HTTP ${loginStatus} (expected 200)` };
  if (!hasToken) return { ok: false, message: "the persona sign-in answered 200 without a token" };
  return { ok: true, message: "the persona signs in" };
}

/**
 * The web sign-in (NextAuth on dev.sovkora.com): the web reaches the edge through `ROUTER_URL` (http://core:8088, inside the
 * DO app), so a booting or unreachable core shows here while the public edge API may still answer.
 * @returns {{ ok: boolean, message: string }}
 */
export function webVerdict(status, session, email) {
  if (status !== 200) return { ok: false, message: `the web sign-in answered HTTP ${status} (expected 200)` };
  if (session?.user?.email?.toLowerCase() !== email.toLowerCase()) return { ok: false, message: "the web sign-in gave no session for the persona (the web cannot reach the router?)" };
  return { ok: true, message: "the persona signs in on the web" };
}

/** Signs in the way the browser does (csrf, credentials callback, session), without a browser. */
async function webSignIn(email, pass) {
  const jar = new Map();
  const send = async (path, init = {}) => {
    const cookie = [...jar].map(([k, v]) => `${k}=${v}`).join("; ");
    const res = await fetch(`${WEB}${path}`, { ...init, redirect: "manual", headers: { ...init.headers, cookie } });
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

const fromMac = (key) => execFileSync(join(homedir(), ".krizaka-worktrees/tools/secret.sh"), ["@dev", key], { encoding: "utf8" }).trim();
const secret = (key) => process.env[key] ?? fromMac(key);

async function main(name = "eric") {
  const k = SECRETS[name];
  if (!k) throw new Error(`unknown persona: ${name}`);
  const res = await fetch(`${EDGE}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: secret(`APP_QA_ORAZAKA_${k}_EMAIL`), password: secret(`APP_QA_ORAZAKA_${k}_PASSWORD`) }),
  });
  const body = res.ok ? await res.json().catch(() => ({})) : {};
  const v = verdict(res.status, Boolean(body.token));
  console.log(`${name}: ${v.message}`);
  if (!v.ok) process.exitCode = 1;
  const w = await webSignIn(secret(`APP_QA_ORAZAKA_${k}_EMAIL`), secret(`APP_QA_ORAZAKA_${k}_PASSWORD`));
  console.log(`${name}: ${w.message}`);
  if (!w.ok) process.exitCode = 1;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main(process.argv[2]).catch((e) => {
    console.error(e.message);
    process.exitCode = 1;
  });
}
