/**
 * @file app-routes.ts
 * @description What the proxy knows of the site's addresses (krizaka/orazaka#443): the public ones, and the pages of the
 * signed-in app. A visitor without a session who asks for an address that is neither is not sent to the login page (that
 * answered 200 with the sign-in form for any typo): the page does not exist, and `src/app/not-found.tsx` says so with a 404.
 * `src/__tests__/app-routes.test.ts` checks that every route folder of `src/app` is listed in one of the two.
 */

/** Route prefixes that do NOT require authentication. */
export const PUBLIC_PREFIXES = [
  "/login",
  "/register",
  "/api/", // All API routes (auth, BFF proxy, etc.)
  "/.well-known/", // Universal links / App Links files (app-links.json)
  "/_next/", // Next.js internals (static, image, RSC payloads, HMR)
  "/favicon",
  "/logo.svg",
  "/privacy",
  "/terms",
  "/contact",
  "/docs",
  "/mobile",
  "/desktop",
  "/pricing",
  "/open-source",
  "/captions/", // the clips' WebVTT (src/app/captions)
  // What search engines and AI assistants read (src/app/robots.ts, sitemap.ts, llms*.txt, opengraph-image.tsx).
  "/robots.txt",
  "/sitemap.xml",
  "/llms",
  "/opengraph-image",
] as const;

/** The first segment of every page of the signed-in app (`src/app/<segment>` and `src/app/(protected)/<segment>`). */
export const APP_SEGMENTS = [
  "automation",
  "chat",
  "dashboard",
  "forgot-password",
  "interceptions",
  "jobs",
  "packs",
  "settings",
  "studios",
  "usage",
  "verify",
] as const;

/** Is this the address of a page of the signed-in app? Anything else that is not public does not exist. */
export function isAppPath(pathname: string): boolean {
  return APP_SEGMENTS.some((segment) => pathname === `/${segment}` || pathname.startsWith(`/${segment}/`));
}
