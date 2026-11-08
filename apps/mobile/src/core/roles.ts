import { humanizeCode } from "./humanize";

/**
 * `ROLE_USER` → `USER`: the authority as the platform names it, without the Spring prefix. The screen words it
 * (`profile.roles.USER` → "Member"); a role nobody has named yet goes through `humanizeRole`.
 */
export function roleName(authority: string): string {
  return authority.replace(/^ROLE_/, "");
}

/** `ROLE_CONTENT_MODERATOR` → `Content moderator` — the fallback for a role the catalogue has no word for. */
export function humanizeRole(authority: string): string {
  const name = roleName(authority);
  return name === "" ? authority : humanizeCode(name);
}
