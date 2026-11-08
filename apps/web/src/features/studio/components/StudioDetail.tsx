"use client";

import Link from "next/link";
import { BackIcon } from "@krizaka/icons";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { CatalogueIcon } from "@/components/icons/CatalogueIcon";
import { useTranslation } from "@/core/context/LocaleContext";
import { usePricingLabel } from "@/features/studio/hooks/usePricingLabel";
import { StudioLoadError, StudioLoading } from "./StudioAsyncState";
import { useStudioDetail } from "@/features/studio/hooks/useStudioDetail";
import { useInstallations } from "@/features/studio/hooks/useInstallations";
import { StudioInstallPanel } from "@/features/studio/components/StudioInstallPanel";
import { ConsentNotice } from "@/features/studio/consent/ConsentNotice";
import { StudioRunPanel } from "@/features/studio/components/StudioRunPanel";
import { StudioVersionList } from "@/features/studio/components/StudioVersionList";
import { useConfigSchema } from "@/features/studio/hooks/useConfigSchema";
import { PackKindSchema } from "@krizaka/orazaka-shared";

interface StudioDetailProps {
  studioKey: string;
}

/**
 * The Studio detail screen: everything needed to decide to install.
 *
 * The install action itself lands in phase 2 — this screen deliberately shows the
 * cost, the copy and the lock state first, because the decision is made here and
 * the button is only its conclusion.
 */
export function StudioDetail({ studioKey }: Readonly<StudioDetailProps>) {
  const { t } = useTranslation();
  const pricingLabel = usePricingLabel();
  const { studio, versions, isLoading, notFound, hasError, reload } = useStudioDetail(studioKey);
  const { findFor, reload: reloadInstallations } = useInstallations();
  const installation = studio ? findFor(studio.studioKey) : undefined;
  const hasSettings = useConfigSchema(studio?.configSchema ?? null).length > 0;

  if (isLoading) {
    return <StudioLoading />;
  }

  if (notFound || hasError || !studio) {
    return <StudioLoadError onRetry={notFound ? undefined : reload} />;
  }

  // Run where the Studio is open to this person, unless it still has its settings to ask (« Set up » comes first).
  const canRun =
    !studio.locked &&
    studio.latestVersion !== null &&
    (installation !== undefined || studio.kind === PackKindSchema.enum.TOOLKIT || !hasSettings);

  return (
    <div className="flex flex-col gap-6">
      <Button asChild variant="ghost" size="sm" className="self-start">
        <Link href="/studios">
          <BackIcon size={15} />
          {t.studio.back}
        </Link>
      </Button>

      <header className="flex items-start gap-4">
        <CatalogueIcon iconKey={studio.iconKey} size={28} className="mt-1 flex-shrink-0 text-fg-accent" />
        <div className="flex flex-col gap-1.5">
          <h1 className="font-display text-2xl font-bold tracking-[-0.02em] text-fg">{studio.label}</h1>
          {studio.tagline && <p className="text-[15px] text-fg-secondary">{studio.tagline}</p>}
          <div className="flex flex-wrap items-center gap-2 pt-0.5">
            <Badge>{t.studio.professions[studio.profession] ?? studio.profession}</Badge>
            <Badge tone={studio.pricing === "PAID" ? "neutral" : "accent"}>{pricingLabel[studio.pricing]}</Badge>
          </div>
        </div>
      </header>

      {studio.description && (
        <p className="max-w-2xl text-sm leading-relaxed text-fg-secondary">{studio.description}</p>
      )}

      {studio.estimatedCredits > 0 && (
        <div className="flex items-baseline gap-2 self-start rounded-xl border border-border-default bg-surface-1 px-4 py-3">
          <span className="text-xs font-medium text-fg-secondary">{t.studio.estimatedCost}</span>
          <span className="font-display text-xl font-bold tabular-nums text-fg">{studio.estimatedCredits}</span>
          <span className="text-xs text-fg-muted">{t.studio.creditsPerRun}</span>
        </div>
      )}

      <StudioInstallPanel
        studio={studio}
        installation={installation}
        onChanged={() => {
          reloadInstallations();
          reload();
        }}
      />

      <ConsentNotice
        studioKey={studio.studioKey}
        installation={installation}
        onChanged={() => {
          reloadInstallations();
          reload();
        }}
      />

      {canRun && <StudioRunPanel studio={studio} installation={installation} />}

      <StudioVersionList versions={versions} />
    </div>
  );
}
