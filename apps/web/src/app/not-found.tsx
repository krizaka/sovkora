"use client";

import Link from "next/link";
import { ActionBar } from "@krizaka/ui/action-bar";
import { ErrorPage } from "@krizaka/ui/error-page";
import { Mascot } from "@krizaka/ui/mascots";
import { useTranslation } from "@/core/context/LocaleContext";
import { LandingFooter } from "@/features/landing/components/LandingFooter";
import { LandingNav } from "@/features/landing/components/LandingNav";

/**
 * An address that does not exist (krizaka/orazaka#443): the Error template of @krizaka/ui — the code drawn, the pigeon that
 * lost its way, one sentence and the ways back — answered with a real 404 (the proxy no longer sends a visitor to the login
 * page for an address it does not know, `core/config/app-routes.ts`).
 */
export default function NotFound() {
  const { t } = useTranslation();
  const n = t.notFound;
  return (
    <>
      <LandingNav />
      <main id="main" className="bg-surface-0 text-fg">
        <ErrorPage
          code="404"
          mascot={<Mascot bird="pigeon" mood="empty" size={96} />}
          title={n.title}
          text={n.text}
          actions={
            <ActionBar
              label={n.label}
              primaryFirst
              linkAs={Link}
              actions={[
                { href: "/", label: n.home },
                { href: "/docs", label: n.docs },
                { href: "/login", label: n.signIn },
              ]}
            />
          }
        />
      </main>
      <LandingFooter />
    </>
  );
}
