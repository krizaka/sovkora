"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Tabs } from "@krizaka/ui/tabs";
import { consoleCopy } from "@/console/core/copy/console";
import { activeGroup, activeScreen } from "@/console/core/config/sections";

/**
 * The screens of the part of the console the page is in (People · Billing under Accounts, Models · Studios under
 * Catalogue), as the kit's tabs under the bar: one keyboard-reachable row, the screen you are on lit. Nothing for a
 * section that is a single screen.
 */
export function ConsoleSubnav() {
  const pathname = usePathname();
  const group = activeGroup(pathname);
  const screen = activeScreen(pathname);
  if (!group || group.children.length === 0 || !screen) return null;
  return (
    <div className="mx-auto w-full max-w-7xl px-4 pt-4 sm:px-6">
      <Tabs.Nav label={consoleCopy.nav.sections} current={screen.href} variant="underline">
        {group.children.map((child) => (
          <Tabs.NavLink key={child.id} href={child.href} exact asChild>
            <Link href={child.href}>{consoleCopy.nav[child.id as keyof typeof consoleCopy.nav] as string}</Link>
          </Tabs.NavLink>
        ))}
      </Tabs.Nav>
    </div>
  );
}
