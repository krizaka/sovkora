"use client";

import { useContext } from "react";
import Link from "next/link";
import { SessionContext } from "next-auth/react";
import { Avatar } from "@krizaka/ui/avatar";
import { Button } from "@krizaka/ui/button";
import { ProductBarActions, useSessionHint } from "@krizaka/ui/nav-bar";
import { useTranslation } from "@/core/context/LocaleContext";
import { useAuthFlags } from "@/core/context/AuthFlagsContext";

/**
 * The one action of the public bar: using the app. A visitor sees « Sign in » while sign-up is closed and « Try
 * Sovkora » once it is open (`AUTH_SIGNUP_ENABLED`); a signed-in person sees « Open the app » and their avatar. The
 * session is read in the browser (next-auth's `/api/auth/session`), so every public page stays static; it is only a
 * display hint — the app and the API check the real session on every request.
 */
export function BarActions({ placement }: Readonly<{ placement: "bar" | "panel" }>) {
  const { t } = useTranslation();
  const n = t.landing.nav;
  const flags = useAuthFlags();
  // Read the provider directly: a page rendered without one (tests, error pages) is simply signed out.
  const session = useContext(SessionContext);
  const isAuthenticated = session?.status === "authenticated";
  const loading = session?.status === "loading";
  const user = session?.data?.user;
  const panel = placement === "panel";
  const hinted = useSessionHint();

  // Loading is neither state (krizaka-ui#800): a signed-in person never sees « Sign in » flash before the avatar. The place is reserved.
  // With the proxy's hint (a session cookie exists, krizaka-ui#827) the reserved place is the account's own size.
  if (loading)
    return (
      <span
        data-testid="bar-session"
        data-session="pending"
        data-hint={hinted ? "signed-in" : undefined}
        aria-hidden="true"
        className={hinted ? "inline-block size-9 rounded-full bg-surface-2" : "inline-block h-9 min-w-24"}
      />
    );

  if (isAuthenticated) {
    const initial = ((user?.name || user?.email || "U")[0] ?? "U").toUpperCase();
    return (
      <>
        <Avatar
          size="sm"
          alt={user?.name || user?.email || ""}
          defaultAvatar={{ brand: "orazaka", seed: user?.id ?? user?.email ?? user?.name ?? undefined }}
          fallback={initial}
        />
        <Button
          asChild
          variant="primary"
          size={panel ? "md" : "sm"}
          className={panel ? "mt-2 w-full font-bold" : "ml-1 hidden font-bold md:inline-flex"}
        >
          <Link href="/">{n.openApp}</Link>
        </Button>
      </>
    );
  }

  return (
    <ProductBarActions
      signIn={{ href: "/login", label: n.signIn }}
      tryIt={flags.signup ? { href: "/register", label: n.tryIt } : undefined}
      signupOpen={flags.signup}
      linkAs={Link}
      placement={placement}
    />
  );
}
