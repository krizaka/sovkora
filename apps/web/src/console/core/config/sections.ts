/**
 * The console's navigation as data (Dev preview `admin.ux-review`): the operator's jobs, in five places —
 * what is happening (Supervision), what is running (Operations), who and what they hold (Accounts: People, Billing),
 * what is offered (Catalogue: Models, Studios, Law — the rules the engine checks documents with, ADR-089 §8), what was done (Audit). The bar shows the five; a group of two opens its
 * two screens as tabs under the bar. Every route appears once (`sections.test.mts`).
 */
export interface ConsoleSection {
  id: string;
  href: string;
}

export interface ConsoleGroup extends ConsoleSection {
  /** The screens a group holds (two or more), shown as tabs under the bar; none for a single screen. */
  children: readonly ConsoleSection[];
}

export const CONSOLE_GROUPS: readonly ConsoleGroup[] = [
  { id: "supervision", href: "/", children: [] },
  {
    id: "operations",
    href: "/operations",
    children: [
      { id: "operations", href: "/operations" },
      { id: "tasks", href: "/tasks" },
      { id: "engine", href: "/engine" },
    ],
  },
  {
    id: "accounts",
    href: "/users",
    children: [
      { id: "users", href: "/users" },
      { id: "billing", href: "/billing" },
    ],
  },
  {
    id: "catalogue",
    href: "/models",
    children: [
      { id: "models", href: "/models" },
      { id: "studios", href: "/studios" },
      { id: "law", href: "/law" },
    ],
  },
  { id: "audit", href: "/audit", children: [] },
];

/** Every screen of the console, once. */
export function consoleRoutes(): string[] {
  return CONSOLE_GROUPS.flatMap((g) => (g.children.length > 0 ? g.children.map((c) => c.href) : [g.href]));
}

const under = (pathname: string, href: string) => pathname === href || pathname.startsWith(`${href}/`);

/** The address bar says `/studios`, the rendered tree `/console/studios`: either way the same screen. */
export const consolePath = (pathname: string) => pathname.replace(/^\/console(?=\/|$)/, "") || "/";

/** The group the page belongs to — the home only on `/` itself, every other one for its screens and what is under them. */
export function activeGroup(pathname: string): ConsoleGroup | null {
  const p = consolePath(pathname);
  if (p === "/") return CONSOLE_GROUPS[0];
  return (
    CONSOLE_GROUPS.find(
      (g) => g.href !== "/" && (g.children.length > 0 ? g.children : [g]).some((s) => under(p, s.href)),
    ) ?? null
  );
}

/** The screen of the group the page is on, for the tabs. */
export function activeScreen(pathname: string): ConsoleSection | null {
  const p = consolePath(pathname);
  return activeGroup(p)?.children.find((c) => under(p, c.href)) ?? null;
}
