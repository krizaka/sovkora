"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { OrazakaLogo } from "@krizaka/ui";
import { Mascot } from "@krizaka/ui/mascots";
import { AuthLayout } from "@krizaka/ui/auth-layout";
import { DevelopmentStatus } from "@/components/DevelopmentStatus";
import { LocaleSwitch } from "@/components/LocaleSwitch";
import { useTranslation } from "@/core/context/LocaleContext";

/** The three promises of the brand panel, each opening its feature page. */
const PROMISES = [
  { key: "heroFeature1", route: "absolute-privacy" },
  { key: "heroFeature2", route: "unified-engine" },
  { key: "heroFeature3", route: "infinite-reach" },
] as const;

/**
 * The entry screen of the web client — sign-in, sign-up, password reset, verification — on `@krizaka/ui/auth-layout`,
 * wearing Sovkora (`.brand-orazaka`): its animated mark in the brand panel (static under reduced motion, the mark's
 * own rule), the promise, the three proofs; the form beside it, the legal links and the language under it.
 */
export function AuthScreen({
  title,
  description,
  footer,
  children,
}: Readonly<{ title: ReactNode; description?: ReactNode; footer?: ReactNode; children: ReactNode }>) {
  const { t } = useTranslation();
  return (
    <div className="brand-orazaka">
      <AuthLayout
        brand={
          <>
            <OrazakaLogo size={28} animated={false} /> Sovkora
          </>
        }
        promise={t.auth.heroHeadline}
        aside={
          <div className="flex flex-col gap-6">
            <Mascot bird="duck" mood="welcome" size={160} />
            <p>{t.auth.heroTagline}</p>
            <ul className="flex flex-col gap-2">
              {PROMISES.map(({ key, route }) => (
                <li key={key}>
                  <Link href={`/features/${route}`} className="underline-offset-4 hover:text-fg hover:underline">
                    {t.auth[key].split(" — ")[0]}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        }
        asideFooter={
          <>
            © {new Date().getFullYear()} Sovkora · {t.landing.footer.license} ·{" "}
            <a
              href="https://www.krizaka.com/"
              target="_blank"
              rel="noopener noreferrer"
              className="underline underline-offset-2 hover:text-fg"
            >
              {t.landing.footer.by}
            </a>
          </>
        }
        title={title}
        description={description}
        footer={footer}
        legal={
          <span className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1">
            <Link href="/privacy" className="hover:text-fg">
              {t.auth.legalPrivacy}
            </Link>
            <Link href="/terms" className="hover:text-fg">
              {t.auth.legalTerms}
            </Link>
            {/* /contact answers a redirect to www.krizaka.com: prefetching it is a CORS error on every visit. */}
            <Link href="/contact" prefetch={false} className="hover:text-fg">
              {t.auth.legalContact}
            </Link>
          </span>
        }
      >
        <DevelopmentStatus className="mb-4 text-center" />
        {children}
        {/* Outside `legal`: AuthLayout wraps that in a <p>, and the switch is a group of links in a <div>. */}
        <div className="mt-6 flex justify-center">
          <LocaleSwitch variant="inline" />
        </div>
      </AuthLayout>
    </div>
  );
}
