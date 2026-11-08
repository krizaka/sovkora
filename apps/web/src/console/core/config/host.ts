/**
 * @file host.ts
 * @description Two sites, one app: www.orazaka.com (the product) and admin.orazaka.com (the operator console). The
 * proxy decides by the host: on the admin host every page is served from the internal `/console` tree (`/studios` is
 * `/console/studios`), on any other host that tree does not exist (404). Pure, so it is tested without a server.
 */
export const CONSOLE_PREFIX = "/console";

export type HostRoute = { kind: "next" } | { kind: "rewrite"; path: string } | { kind: "notFound" };

/** `admin.` as the first label of the Host header (port ignored). */
export const isAdminHost = (host: string | null | undefined): boolean => /^admin\./i.test(host ?? "");

/** Is `pathname` inside the internal console tree (`/console` or `/console/…`, not `/consoles`)? */
export const inConsoleTree = (pathname: string): boolean =>
  pathname === CONSOLE_PREFIX || pathname.startsWith(`${CONSOLE_PREFIX}/`);

export function routeFor(host: string | null | undefined, pathname: string): HostRoute {
  if (inConsoleTree(pathname)) return { kind: "notFound" };
  if (!isAdminHost(host)) return { kind: "next" };
  return { kind: "rewrite", path: pathname === "/" ? CONSOLE_PREFIX : `${CONSOLE_PREFIX}${pathname}` };
}
