import "server-only";
import { authClosed, type AuthFlags } from "@/core/config/auth-flags";
import { brandName } from "./brand";

/** The setting key, shared with the Java services and `@krizaka/settings` (`DISPLAY_NAME_KEY`). */
const SETTING_KEY = "brand.displayName";
/** The name is read again at most every five minutes: a change of the setting shows without a deploy. */
const REVALIDATE_SECONDS = 300;

type Source = Record<string, string | undefined>;

/**
 * The name to show, read on the server (root layout). Order: the setting served by the edge (`brand.displayName`,
 * public read, request time) when the site reaches the backend, else the instance value `APP_BRAND_DISPLAY_NAME`, else
 * the commercial name. Any failure falls back: the name never blocks a page.
 */
export async function readBrandName(
  flags: AuthFlags,
  source: Source = process.env,
  fetcher: typeof fetch = fetch,
): Promise<string> {
  const fromEdge = authClosed(flags) ? null : await readEdgeName(source, fetcher);
  return brandName(fromEdge ?? source.APP_BRAND_DISPLAY_NAME);
}

/** The public setting served by the edge, or null when it is unreachable, refused or empty. */
async function readEdgeName(source: Source, fetcher: typeof fetch): Promise<string | null> {
  const edgeUrl = source.ROUTER_URL || source.GATEWAY_URL || "http://localhost:8088";
  try {
    const response = await fetcher(`${edgeUrl}/api/v1/settings/public/${SETTING_KEY}`, {
      next: { revalidate: REVALIDATE_SECONDS },
    });
    if (!response.ok) {
      return null;
    }
    const body = (await response.json()) as { value?: unknown };
    return typeof body.value === "string" && body.value.trim() ? body.value : null;
  } catch {
    // The edge is unreachable: the instance value answers.
    return null;
  }
}
