"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { LockIcon } from "@krizaka/icons";
import { Button } from "@krizaka/ui/button";
import { OrazakaLogo } from "@krizaka/ui";
import { useAuthFlags } from "@/core/context/AuthFlagsContext";
import { useTranslation } from "@/core/context/LocaleContext";

/** The calm notice of a closed door: Sovkora is in development, accounts are not open on this site. */
export function AuthClosed() {
  const { t } = useTranslation();
  const c = t.landing.closed;
  return (
    <main className="flex min-h-screen items-center justify-center bg-surface-0 px-4 py-16 text-fg">
      <article className="flex max-w-lg flex-col items-center gap-6 text-center">
        <OrazakaLogo size={72} />
        <h1 className="font-display text-2xl font-bold tracking-tight text-balance sm:text-3xl">{c.title}</h1>
        <p className="text-base leading-relaxed text-pretty text-fg-secondary">{c.body}</p>
        <nav className="flex flex-wrap justify-center gap-3" aria-label={c.title}>
          <Button asChild variant="primary" shape="pill">
            <Link href="/docs">{c.docs}</Link>
          </Button>
          <Button asChild variant="ghost" shape="pill">
            <Link href="/">{c.home}</Link>
          </Button>
        </nav>
        <LockIcon size={16} className="text-fg-muted" />
      </article>
    </main>
  );
}

/**
 * Renders its children only when the entry point is open (`login`: sign-in, forgot-password, verify; `signup`:
 * register) — otherwise the closed notice, and none of the children mount, so nothing calls the backend.
 */
export function AuthGate({ flag, children }: Readonly<{ flag: "login" | "signup"; children: ReactNode }>) {
  const flags = useAuthFlags();
  return flags[flag] ? <>{children}</> : <AuthClosed />;
}
