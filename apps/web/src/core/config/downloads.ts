/**
 * @file downloads.ts
 * @description Whether the Mac build is handed out. The admin's setting is the source (`downloads.mac-enabled` of the studio
 * runtime config, flipped in the console and audited there), read server-side from the edge's public settings endpoint
 * (`GET /api/v1/studios/public/downloads`: no token, an allow-list of keys, cached a minute). `APP_DOWNLOADS_MAC_ENABLED`
 * (bounded context APP_, off unless exactly `true`) is only the bootstrap default: used when no edge is configured or the
 * call fails. Off, the Download page shows a disabled button and « In testing — not distributed yet », carries no link to
 * the file, and `/desktop/download` answers 404.
 */
export interface DownloadSwitches {
  macEnabled: boolean;
}

type Vars = Record<string, string | undefined>;

/** The bootstrap default: the environment value, off unless it is exactly `true`. */
export function readDownloadsFromEnv(vars: Vars = process.env): DownloadSwitches {
  return { macEnabled: (vars.APP_DOWNLOADS_MAC_ENABLED ?? "").trim().toLowerCase() === "true" };
}

interface ReadOptions {
  vars?: Vars;
  fetcher?: typeof fetch;
}

/** The switches the page and the file route obey: the admin's setting when the edge answers, the environment default otherwise. */
export async function readDownloads({
  vars = process.env,
  fetcher = fetch,
}: ReadOptions = {}): Promise<DownloadSwitches> {
  const fallback = readDownloadsFromEnv(vars);
  const configured = vars.ROUTER_URL || vars.GATEWAY_URL || "";
  const edge = configured.endsWith("/") ? configured.slice(0, -1) : configured;
  if (!edge) {
    return fallback;
  }
  try {
    const response = await fetcher(`${edge}/api/v1/studios/public/downloads`, { next: { revalidate: 60 } });
    if (!response.ok) {
      return fallback;
    }
    const body = (await response.json()) as { macEnabled?: unknown } | null;
    return typeof body?.macEnabled === "boolean" ? { macEnabled: body.macEnabled } : fallback;
  } catch {
    return fallback;
  }
}
