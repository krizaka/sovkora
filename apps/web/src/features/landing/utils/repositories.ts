/**
 * @file repositories.ts
 * @description The public Sovkora repositories, read from the GitHub organisation at build time (and refreshed hourly) —
 * never typed here. When GitHub cannot be read the page says so and links the organisation.
 */

export const ORG_URL = "https://github.com/krizaka";
const API = "https://api.github.com/orgs/krizaka/repos?type=public&per_page=100&sort=pushed";

export interface GithubRepo {
  name: string;
  description: string | null;
  html_url: string;
  pushed_at: string | null;
  archived: boolean;
  fork: boolean;
}

export interface ListedRepo {
  name: string;
  description: string;
  url: string;
  pushedAt: string | null;
}

/** The live repositories of the Sovkora family (name starts with `orazaka`), most recently pushed first. */
export function pickListedRepos(repos: readonly GithubRepo[]): ListedRepo[] {
  return repos
    .filter((r) => r.name.startsWith("orazaka") && !r.archived && !r.fork)
    .map((r) => ({ name: r.name, description: r.description ?? "", url: r.html_url, pushedAt: r.pushed_at }))
    .sort((a, b) => Date.parse(b.pushedAt ?? "0") - Date.parse(a.pushedAt ?? "0"));
}

/** The repositories to list, or null when GitHub cannot be read. */
export async function loadListedRepos(): Promise<ListedRepo[] | null> {
  try {
    const res = await fetch(API, { headers: { Accept: "application/vnd.github+json" }, next: { revalidate: 3600 } });
    if (!res.ok) return null;
    const repos = pickListedRepos((await res.json()) as GithubRepo[]);
    return repos.length > 0 ? repos : null;
  } catch {
    return null;
  }
}
