"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { signOut } from "next-auth/react";
import { CodeNameLine, OrazakaLogo } from "@krizaka/ui";
import { IconButton } from "@krizaka/ui/button";
import { NavBar } from "@krizaka/ui/nav-bar";
import { ThemeToggle } from "@krizaka/ui/theme";
import { LogoutIcon } from "@krizaka/icons";
import { consoleCopy } from "@/console/core/copy/console";
import { consoleCodeName } from "@/console/core/config/code-name";
import { APP_ENV } from "@/console/core/config/version";
import { CONSOLE_GROUPS, activeGroup } from "@/console/core/config/sections";

const SECTIONS = CONSOLE_GROUPS.map((g) => ({
  href: g.href,
  id: g.id,
  label: consoleCopy.nav[g.id as keyof typeof consoleCopy.nav] as string,
}));

/**
 * The console's bar (@krizaka/ui NavBar, full width): the Sovkora mark, its five sections (config/sections.ts) with the
 * light gliding to the current one, the environment badge (DEV), the theme toggle and sign-out; a disclosure panel below
 * `md`. A private console: no family switcher (NavBar.Family) and no family footer — they are for the public sites.
 */
export function ConsoleNav() {
  // The address bar says `/studios`; the rendered tree says `/console/studios` — either way the same section.
  const group = activeGroup(usePathname());
  const active = (id: string) => group?.id === id;
  const copy = consoleCopy.nav;
  const codeName = consoleCodeName();

  return (
    <section className="sticky top-0 z-40">
      <NavBar.Root label={copy.label} floating={false}>
        <NavBar.Brand asChild>
          <Link href="/">
            <OrazakaLogo size={24} />
            <span className="flex flex-col leading-tight">
              <span>
                {consoleCopy.product} <span className="font-normal text-fg-muted">{consoleCopy.console}</span>
              </span>
              {codeName ? <CodeNameLine>{codeName}</CodeNameLine> : null}
            </span>
          </Link>
        </NavBar.Brand>
        {/* NAV-SYS: a private console shows the environment (DEV) and never the family panel. */}
        {/* inline-flex: the kit hides the badge below 640 px, but an operator on a phone must still see the environment. */}
        {APP_ENV !== "production" && <NavBar.Badge env={APP_ENV} className="inline-flex" />}
        <NavBar.Items>
          {SECTIONS.map((section) => (
            <NavBar.Link key={section.href} asChild active={active(section.id)}>
              <Link href={section.href}>{section.label}</Link>
            </NavBar.Link>
          ))}
        </NavBar.Items>
        <NavBar.Actions>
          <ThemeToggle variant="ghost" label={(mode) => copy.theme[mode]} />
          <IconButton variant="ghost" label={copy.signOut} onClick={() => void signOut({ callbackUrl: "/login" })}>
            <LogoutIcon size={18} />
          </IconButton>
          <NavBar.Mobile openLabel={copy.open} closeLabel={copy.close}>
            {SECTIONS.map((section) => (
              <NavBar.MobileLink key={section.href} asChild active={active(section.id)}>
                <Link href={section.href}>{section.label}</Link>
              </NavBar.MobileLink>
            ))}
          </NavBar.Mobile>
        </NavBar.Actions>
      </NavBar.Root>
    </section>
  );
}
