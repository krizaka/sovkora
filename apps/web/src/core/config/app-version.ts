/**
 * @file app-version.ts
 * @description `APP_VERSION` — the version of the app shown in the footer of every deployed site: the version of
 * package.json and the short sha of the commit, set once at build by next.config.ts (never typed).
 */
import { execFileSync } from "node:child_process";
import { existsSync } from "node:fs";

/** Where git lives on a build host: an absolute path, never a lookup through a PATH the build does not own. */
const GIT_CANDIDATES = ["/usr/bin/git", "/usr/local/bin/git", "/opt/homebrew/bin/git"];

/** `1.0.0+ab12cd3`; without a sha, the bare version. */
export function formatVersion(version: string, sha?: string): string {
  const short = (sha ?? "").trim().slice(0, 7);
  return short ? `${version}+${short}` : version;
}

/** The sha of the build: an explicit `APP_COMMIT` (the platform provides it), CI's, or the checkout's own. */
export function resolveSha(env: Record<string, string | undefined> = process.env): string {
  // An unresolved `${…}` placeholder (a platform variable that did not bind) is not a sha.
  const fromEnv = [env.APP_COMMIT, env.GITHUB_SHA, env.COMMIT_HASH, env.SOURCE_COMMIT].find(
    (v) => v && !v.startsWith("$"),
  );
  if (fromEnv) return fromEnv;
  try {
    const git = GIT_CANDIDATES.find((candidate) => existsSync(candidate));
    if (!git) return "";
    return execFileSync(git, ["rev-parse", "--short", "HEAD"], { stdio: ["ignore", "pipe", "ignore"] })
      .toString()
      .trim();
  } catch {
    return "";
  }
}
