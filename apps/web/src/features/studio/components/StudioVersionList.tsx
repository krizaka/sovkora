"use client";

import { format } from "date-fns";
import { useTranslation } from "@/core/context/LocaleContext";
import type { BlueprintVersion } from "@krizaka/orazaka-shared";

interface StudioVersionListProps {
  versions: BlueprintVersion[];
}

/**
 * The version history.
 *
 * Drafts are dropped here on purpose: the service returns them so the admin
 * console can read the same endpoint, but an unpublished version is not something
 * a user can pin, and listing it would offer an upgrade that cannot happen.
 */
export function StudioVersionList({ versions }: Readonly<StudioVersionListProps>) {
  const { t } = useTranslation();
  const published = versions.filter((version) => version.status !== "DRAFT");

  if (published.length === 0) {
    return null;
  }

  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-xs font-semibold text-fg-muted">{t.studio.versionHistory}</h2>
      <ul className="flex flex-col divide-y divide-border-subtle overflow-hidden rounded-xl border border-border-default bg-surface-1">
        {published.map((version) => (
          <StudioVersionRow key={version.version} version={version} />
        ))}
      </ul>
    </section>
  );
}

interface StudioVersionRowProps {
  version: BlueprintVersion;
}

/** One history row. A sub-component so the list above stays a list (UI standards). */
function StudioVersionRow({ version }: Readonly<StudioVersionRowProps>) {
  return (
    <li className="flex flex-col gap-1 px-4 py-3">
      <div className="flex items-center justify-between gap-3">
        <span className="text-[12px] font-semibold text-fg">{version.version}</span>
        <span className="hud-label text-[10px] text-fg-muted">
          {version.publishedAt ? format(new Date(version.publishedAt), "dd MMM yyyy") : ""}
        </span>
      </div>
    </li>
  );
}
