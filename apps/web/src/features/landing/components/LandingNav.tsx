"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { CodeNameLine, OrazakaLogo } from "@krizaka/ui";
import { NavBar } from "@krizaka/ui/nav-bar";
import { ThemeToggle } from "@krizaka/ui/theme";
import { BarActions } from "@/features/landing/components/BarActions";
import { useLanguageSwitch } from "@/components/LocaleSwitch";
import { useBrand } from "@/core/context/BrandContext";
import { useCodeName } from "@/core/context/CodeNameContext";
import { useTranslation } from "@/core/context/LocaleContext";
import { NAV_DOCS, NAV_GROUPS, NAV_PAGES, entryHref, navActive } from "@/features/landing/utils/site-nav";

export { navActive } from "@/features/landing/utils/site-nav";

const REPO_URL = "https://github.com/krizaka/orazaka-web-client";

/**
 * The bar of the public pages — the family's, the one of Yomizaka and the other products (`site-nav.ts`,
 * docs/ux/information-architecture.md of the workspace): a floating bar. Left: the Sovkora mark and name (home). Then
 * Product ▾ (what it does · Download) · Docs · Pricing · Open source — one lit at a time, the page's own group (a group of
 * three or fewer entries will be inline once Krizaka UI's breakpoint-aware menu ships). Right: the repository, the
 * theme, the language, and the one action — using the app (BarActions). The version is in the footer, not here.
 * Below md: one panel with the same groups in the same order. `onHome` is false off the home page, where the anchors
 * lead back to its sections.
 */
export function LandingNav({ onHome = true }: Readonly<{ onHome?: boolean }>) {
  const { t } = useTranslation();
  const n = t.landing.nav;
  const language = useLanguageSwitch(n.langLabel);
  const brand = useBrand();
  const codeName = useCodeName();
  const active = navActive(usePathname());
  const groups = NAV_GROUPS.map((g) => ({
    id: g.id,
    label: n[g.id],
    entries: g.entries.map((e) => ({
      id: e.id,
      Icon: e.Icon,
      href: entryHref(e, onHome),
      label: n[e.id],
      description: n.menu[e.id],
      active: active.entry === e.id,
    })),
  }));
  // A switch, not an object keyed by the modes: the UI ratchet counts a key named like the Tailwind variant.
  const themeLabel = (mode: "dark" | "light" | "system") => {
    switch (mode) {
      case "dark":
        return t.header.themeDark;
      case "light":
        return t.header.themeLight;
      default:
        return t.header.themeSystem;
    }
  };

  return (
    <div className="pointer-events-none sticky top-0 z-40 px-3 pt-3">
      <NavBar.Root label={brand} className="pointer-events-auto mx-auto max-w-(--kz-page-max,72rem)">
        <NavBar.Brand asChild>
          <Link href="/" aria-label={n.home}>
            <OrazakaLogo size={26} />
            <span className="flex flex-col leading-tight">
              <span>{brand}</span>
              {codeName ? <CodeNameLine>{codeName}</CodeNameLine> : null}
            </span>
          </Link>
        </NavBar.Brand>

        <NavBar.Items>
          {groups.map((g) => (
            <NavBar.Menu key={g.id} value={g.id} label={g.label} active={active.top === g.id}>
              {g.entries.map((e) => (
                <NavBar.MenuLink
                  key={e.id}
                  asChild
                  title={e.label}
                  description={e.description}
                  icon={<e.Icon size={18} />}
                  active={e.active}
                >
                  <Link href={e.href} />
                </NavBar.MenuLink>
              ))}
            </NavBar.Menu>
          ))}
          <NavBar.Link asChild active={active.top === "docs"}>
            <Link href={NAV_DOCS.path}>{n.docs}</Link>
          </NavBar.Link>
          {NAV_PAGES.map((p) => (
            <NavBar.Link key={p.id} asChild active={active.top === p.id}>
              <Link href={p.path}>{n[p.id]}</Link>
            </NavBar.Link>
          ))}
        </NavBar.Items>

        <NavBar.Actions className="gap-1">
          <NavBar.Repo
            href={REPO_URL}
            label={n.repo}
            target="_blank"
            rel="noopener noreferrer"
            className="hidden lg:inline-flex"
          />
          <ThemeToggle variant="ghost" className="hidden md:inline-flex" label={themeLabel} />
          <span className="hidden md:inline-flex">
            <NavBar.Language {...language} />
          </span>
          <BarActions placement="bar" />

          <NavBar.Mobile
            openLabel={n.openMenu}
            closeLabel={n.closeMenu}
            className="max-h-[calc(100dvh-6rem)] gap-1 overflow-y-auto overscroll-contain p-3"
          >
            {groups.map((g) => (
              <NavBar.MobileGroup key={g.id} label={g.label}>
                {g.entries.map((e) => (
                  <NavBar.MobileLink key={e.id} asChild active={e.active}>
                    <Link href={e.href}>{e.label}</Link>
                  </NavBar.MobileLink>
                ))}
              </NavBar.MobileGroup>
            ))}
            <span aria-hidden className="my-1 h-px bg-border-subtle" />
            <NavBar.MobileLink asChild active={active.top === "docs"}>
              <Link href={NAV_DOCS.path}>{n.docs}</Link>
            </NavBar.MobileLink>
            {NAV_PAGES.map((p) => (
              <NavBar.MobileLink key={p.id} asChild active={active.top === p.id}>
                <Link href={p.path}>{n[p.id]}</Link>
              </NavBar.MobileLink>
            ))}
            <NavBar.MobilePreferences label={n.preferences}>
              <ThemeToggle variant="ghost" label={themeLabel} />
              <NavBar.Language {...language} />
            </NavBar.MobilePreferences>
            <BarActions placement="panel" />
          </NavBar.Mobile>
        </NavBar.Actions>
      </NavBar.Root>
    </div>
  );
}
