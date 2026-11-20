"use client";

import { Button } from "@krizaka/ui/button";
import { ErrorPage } from "@krizaka/ui/error-page";
import { Mascot } from "@krizaka/ui/mascots";
import { useTranslation } from "@/core/context/LocaleContext";

/**
 * A page of the console that failed to render (krizaka-internal#2414): shown inside the application shell (the layout of
 * this folder stays around it, so the navigation is still there) instead of a bare browser-default page, with one way to retry.
 */
export default function ConsoleError({ reset }: Readonly<{ error: Error & { digest?: string }; reset: () => void }>) {
  const { t } = useTranslation();
  const e = t.routeError;
  return (
    <main id="main" className="bg-surface-0 text-fg">
      <ErrorPage
        code="500"
        mascot={<Mascot bird="pigeon" mood="empty" size={96} />}
        title={e.title}
        text={e.text}
        actions={
          <Button variant="primary" onClick={reset}>
            {e.retry}
          </Button>
        }
      />
    </main>
  );
}
