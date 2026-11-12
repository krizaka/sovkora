#!/usr/bin/env node
/**
 * node scripts/check-code-names.mjs [--update] [--root <repo>]
 *
 * The retired code names (krizaka-internal#2684) may only disappear from this repository, never come back.
 * Over `git ls-files`, paths AND contents, case-insensitive; lockfiles are not read.
 *  - scripts/code-names.allow.json : what is kept on purpose (globs `paths`, exact `tokens`), each with a `reason` and the
 *    `row` (id of a line of the mapping table in docs/NAMING.md). An entry that matches nothing, or whose row is missing, fails.
 *  - scripts/code-names.ratchet.json : the hit count per top-level folder. A count may only fall.
 *  - --update lowers the ratchet to today's counts; it never raises one (it creates the file when it does not exist).
 * A hit is one matching line, plus one for a path that matches.
 */
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const CODE_NAMES = /orazaka|yomizaka|akinaza|kachizaka/i;
const LOCKFILE = /(^|\/)(pnpm-lock\.yaml|package-lock\.json|yarn\.lock|Cargo\.lock|[^/]*\.lock)$/;
const ALLOW = "scripts/code-names.allow.json";
const RATCHET = "scripts/code-names.ratchet.json";
const NAMING = "docs/NAMING.md";

export function globToRegExp(glob) {
  let re = "";
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === "*" && glob[i + 1] === "*") {
      i++;
      if (glob[i + 1] === "/") { i++; re += "(?:.*/)?"; } else re += ".*";
    } else if (c === "*") re += "[^/]*";
    else if (c === "?") re += "[^/]";
    else re += c.replace(/[.+^${}()|[\]\\]/g, String.raw`\$&`);
  }
  return new RegExp(`^${re}$`);
}

export const folderOf = (path) => (path.includes("/") ? path.slice(0, path.indexOf("/")) : ".");

/** Every row id (first cell, e.g. K3) of the mapping table of docs/NAMING.md. */
export function namingRows(markdown) {
  return new Set([...markdown.matchAll(/^\|\s*(K\d+)\s*\|/gm)].map((m) => m[1]));
}

/** A string without the allowed tokens it holds; marks each token found as used (the allow-list quoting a token is not a use of it). */
function withoutTokens(s, tokens, path) {
  let out = s;
  for (const t of tokens) {
    if (out.includes(t.e.token)) {
      out = out.split(t.e.token).join("");
      t.used ||= path !== ALLOW;
    }
  }
  return out;
}

/** The code-name hits of one file: its path counts once, then each line of its text. */
function hitsOf(path, text, tokens) {
  let hits = CODE_NAMES.test(withoutTokens(path, tokens, path)) ? 1 : 0;
  if (text === null) {
    return hits;
  }
  for (const line of text.split("\n")) {
    if (CODE_NAMES.test(withoutTokens(line, tokens, path))) {
      hits++;
    }
  }
  return hits;
}

/** @param files {path, text|null}[] (text null: binary or lockfile) */
export function scan(files, allow) {
  const globs = (allow.paths ?? []).map((e) => ({ e, re: globToRegExp(e.glob), used: false }));
  const tokens = (allow.tokens ?? []).map((e) => ({ e, used: false }));
  const counts = {};
  for (const { path, text } of files) {
    const hits = hitsOf(path, text, tokens);
    const glob = globs.find((g) => g.re.test(path));
    if (glob) {
      // the whole file is kept, path and content
      if (hits > 0) {
        glob.used = true;
      }
      continue;
    }
    if (hits > 0) {
      counts[folderOf(path)] = (counts[folderOf(path)] ?? 0) + hits;
    }
  }
  return { counts, deadGlobs: globs.filter((g) => !g.used).map((g) => g.e), deadTokens: tokens.filter((t) => !t.used).map((t) => t.e) };
}

export function judge({ counts, deadGlobs, deadTokens }, allow, ratchet, rows) {
  const errors = [];
  for (const [folder, n] of Object.entries(counts)) {
    const max = ratchet[folder] ?? 0;
    if (n > max) errors.push(`${folder}: ${n} hits, the ratchet allows ${max}. A retired code name came back: rename it (docs/NAMING.md), or keep it on purpose in ${ALLOW} with its row.`);
  }
  for (const e of deadGlobs) errors.push(`dead allowance (matches no hit): path ${e.glob}`);
  for (const e of deadTokens) errors.push(`dead allowance (matches no hit): token ${e.token}`);
  for (const [kind, key, e] of [...(allow.paths ?? []).map((e) => ["path", e.glob, e]), ...(allow.tokens ?? []).map((e) => ["token", e.token, e])]) {
    if (!e.reason) errors.push(`allowance ${kind} ${key} has no reason`);
    if (!rows.has(e.row)) errors.push(`allowance ${kind} ${key}: row ${e.row} is not in the mapping table of ${NAMING}`);
  }
  return errors;
}

/** Today's counts, lowered or kept; a count never goes up, a folder not in the ratchet stays out. */
export function lowered(counts, ratchet, creating) {
  const next = {};
  for (const [folder, max] of Object.entries(ratchet)) {
    const n = counts[folder] ?? 0;
    if (n > 0) next[folder] = Math.min(n, max);
  }
  if (creating) for (const [folder, n] of Object.entries(counts)) next[folder] = n;
  return Object.fromEntries(Object.entries(next).sort(([a], [b]) => a.localeCompare(b)));
}

function readTracked(root) {
  // A fixed, system-owned PATH for the one binary we run (Sonar S4036): never the caller's PATH.
  const env = { ...process.env, PATH: "/usr/bin:/bin:/usr/local/bin:/opt/homebrew/bin" };
  const paths = execFileSync("git", ["ls-files", "-z"], { cwd: root, env, maxBuffer: 1 << 28 }).toString("utf8").split("\0").filter(Boolean);
  return paths.filter((p) => existsSync(join(root, p))).map((path) => {
    if (LOCKFILE.test(path)) return { path, text: null };
    const buf = readFileSync(join(root, path));
    return { path, text: buf.includes(0) ? null : buf.toString("utf8") };
  });
}

export function main(argv, cwd) {
  const rootIdx = argv.indexOf("--root");
  const root = rootIdx >= 0 ? resolve(argv[rootIdx + 1]) : cwd;
  const update = argv.includes("--update");
  const allow = JSON.parse(readFileSync(join(root, ALLOW), "utf8"));
  const hasRatchet = existsSync(join(root, RATCHET));
  const ratchet = hasRatchet ? JSON.parse(readFileSync(join(root, RATCHET), "utf8")).counts : {};
  const rows = existsSync(join(root, NAMING)) ? namingRows(readFileSync(join(root, NAMING), "utf8")) : new Set();
  const result = scan(readTracked(root), allow);
  const total = Object.values(result.counts).reduce((a, b) => a + b, 0);

  if (update) {
    const next = lowered(result.counts, ratchet, !hasRatchet);
    writeFileSync(join(root, RATCHET), `${JSON.stringify({ counts: next }, null, 2)}\n`);
    console.log(`ratchet written: ${Object.values(next).reduce((a, b) => a + b, 0)} hits in ${Object.keys(next).length} folders`);
  }
  const errors = judge(result, allow, update ? lowered(result.counts, ratchet, !hasRatchet) : ratchet, rows);
  for (const e of errors) console.error(`CODE NAME: ${e}`);
  const fallen = Object.entries(ratchet).filter(([f, max]) => (result.counts[f] ?? 0) < max).map(([f]) => f);
  if (!update && fallen.length && !errors.length) console.log(`the count fell in ${fallen.join(", ")}: run \`node scripts/check-code-names.mjs --update\` and commit ${RATCHET}.`);
  console.log(`code names: ${total} hits, ${errors.length} problems`);
  return errors.length ? 1 : 0;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) process.exit(main(process.argv.slice(2), process.cwd()));
