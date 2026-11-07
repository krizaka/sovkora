"use client";

import Link from "next/link";
import { Button } from "@krizaka/ui/button";
import { Mascot } from "@krizaka/ui/mascots";
import { useTranslation } from "@/core/context/LocaleContext";

/** No job yet: the pigeon waits for its first delivery, and the way to start one is one click away. */
export function JobsEmpty() {
  const { t } = useTranslation();
  return (
    <div role="status" className="flex flex-col items-center gap-3 py-12 text-center">
      <Mascot bird="pigeon" mood="empty" size={112} />
      <p className="text-sm font-semibold text-fg">{t.showcase.jobs.emptyTitle}</p>
      <p className="max-w-md text-[13px] text-fg-secondary">{t.showcase.jobs.emptyBody}</p>
      <Button asChild variant="primary" size="sm">
        <Link href="/studios">{t.showcase.jobs.emptyCta}</Link>
      </Button>
    </div>
  );
}
