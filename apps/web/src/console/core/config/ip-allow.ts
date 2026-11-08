/**
 * @file ip-allow.ts
 * @description The network gate of the console, pure: only the client addresses listed in `AUTH_ALLOWED_IPS`
 * (comma-separated) reach it. The client address is DigitalOcean's `do-connecting-ip`, set by its edge, never by the
 * client; locally (not production) the first `x-forwarded-for` hop. In production an empty list lets nobody in.
 */

export function parseAllowList(value: string | undefined): string[] {
  return (value ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}

export function clientIp(headers: { get(name: string): string | null }, production: boolean): string | null {
  const edge = headers.get("do-connecting-ip")?.trim();
  if (edge) return edge;
  if (production) return null;
  return headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "127.0.0.1";
}

export function ipAllowed(ip: string | null, allowList: string[], production: boolean): boolean {
  if (allowList.length === 0) return !production;
  return ip !== null && allowList.includes(ip);
}
