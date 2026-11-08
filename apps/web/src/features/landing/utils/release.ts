/**
 * @file release.ts
 * @description The Sovkora Desktop download, read from the GitHub releases of krizaka/orazaka at build time (and
 * refreshed hourly): the latest release tagged `desktop-*`, its `.dmg` and the SHA-256 published beside it. Never a
 * version or a URL typed here — when no release can be read the page says so and links the releases page.
 */

export const RELEASES_URL = "https://github.com/krizaka/orazaka/releases";
const API = "https://api.github.com/repos/krizaka/orazaka/releases?per_page=30";

export interface GithubAsset {
  name: string;
  browser_download_url: string;
  size: number;
}
export interface GithubRelease {
  tag_name: string;
  draft: boolean;
  prerelease: boolean;
  published_at: string | null;
  html_url: string;
  assets: GithubAsset[];
}

export interface DesktopRelease {
  tag: string;
  version: string;
  prerelease: boolean;
  publishedAt: string | null;
  pageUrl: string;
  dmg: { name: string; url: string; bytes: number };
  /** The URL of the `.sha256` file published beside the `.dmg`. */
  shaUrl: string | null;
}

/** The newest published `desktop-*` release that carries a `.dmg`, or null. */
export function pickDesktopRelease(releases: readonly GithubRelease[]): DesktopRelease | null {
  const candidates = releases
    .filter((r) => !r.draft && r.tag_name.startsWith("desktop-") && r.assets.some((a) => a.name.endsWith(".dmg")))
    .sort((a, b) => Date.parse(b.published_at ?? "0") - Date.parse(a.published_at ?? "0"));
  const r = candidates[0];
  if (!r) return null;
  const dmg = r.assets.find((a) => a.name.endsWith(".dmg"))!;
  const sha = r.assets.find((a) => a.name === `${dmg.name}.sha256`);
  return {
    tag: r.tag_name,
    version: r.tag_name.replace(/^desktop-v?/, ""),
    prerelease: r.prerelease,
    publishedAt: r.published_at,
    pageUrl: r.html_url,
    dmg: { name: dmg.name, url: dmg.browser_download_url, bytes: dmg.size },
    shaUrl: sha?.browser_download_url ?? null,
  };
}

/** The 64 hex characters of a `shasum` line (`<hash>  <file>`), or null. */
export function parseSha256(text: string): string | null {
  const m = /^\s*([0-9a-f]{64})\b/i.exec(text);
  return m ? m[1].toLowerCase() : null;
}

/** The release to offer, with its SHA-256 — null when GitHub cannot be read (the page then links the releases). */
export async function loadDesktopRelease(): Promise<(DesktopRelease & { sha256: string | null }) | null> {
  try {
    const res = await fetch(API, { headers: { Accept: "application/vnd.github+json" }, next: { revalidate: 3600 } });
    if (!res.ok) return null;
    const release = pickDesktopRelease((await res.json()) as GithubRelease[]);
    if (!release) return null;
    let sha256: string | null = null;
    if (release.shaUrl) {
      const s = await fetch(release.shaUrl, { next: { revalidate: 3600 } });
      if (s.ok) sha256 = parseSha256(await s.text());
    }
    return { ...release, sha256 };
  } catch {
    return null;
  }
}
