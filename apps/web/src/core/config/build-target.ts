/**
 * Which build this is (krizaka-internal#4625): the dev app (a server: accounts, API, database) or the public export (static files of
 * the public pages, nothing that can sign in). `next.config.public.mjs` sets it at build time. A module that differs wholesale between
 * the two is swapped by an alias of that config (`public-build/`); this flag is for the few one-line differences — a link to a page
 * the export does not hold, the bar's session action, the DEV tag.
 */
export const publicBuild = process.env.NEXT_PUBLIC_BUILD_TARGET === "public";
