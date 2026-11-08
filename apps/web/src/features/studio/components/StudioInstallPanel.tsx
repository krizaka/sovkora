"use client";

import { useState } from "react";
import { CheckIcon } from "@krizaka/icons";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { useTranslation } from "@/core/context/LocaleContext";
import { StudioApi } from "@/services/studio.api";
import { InstallDialog } from "@/features/studio/components/InstallDialog";
import { StudioLockNotice } from "@/features/studio/components/StudioLockNotice";
import { useConfigSchema } from "@/features/studio/hooks/useConfigSchema";
import type { Installation, StudioDetail } from "@krizaka/orazaka-shared";

interface StudioInstallPanelProps {
  studio: StudioDetail;
  installation: Installation | undefined;
  onChanged: () => void;
}

/**
 * The install / configure / upgrade actions for one Studio.
 *
 * A locked Studio shows the upsell instead of the button, and an unpublished one
 * shows neither: there is no version to pin, so offering an install would produce a
 * failure the user cannot act on.
 *
 * A TOOLKIT Studio the actor is entitled to shows "included" and no install, configure,
 * upgrade or uninstall action (ADR-061): its installation is derived, so each of those
 * would address a row that does not exist. The run form below it is the whole surface.
 *
 * A VERTICAL Studio with nothing to ask has no Install step either: its first run installs it (see
 * `StudioRunPanel`), so the form is the action. Only a Studio whose blueprint declares settings (an agency name, a
 * tone, a signature) asks them once, behind « Set up », before its first run.
 */
export function StudioInstallPanel({ studio, installation, onChanged }: Readonly<StudioInstallPanelProps>) {
  const { t } = useTranslation();
  const [isDialogOpen, setDialogOpen] = useState(false);
  const [isSubmitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const hasSettings = useConfigSchema(studio.configSchema).length > 0;

  const submit = async (config: Record<string, string>) => {
    setSubmitting(true);
    setError(null);
    try {
      if (installation) {
        await StudioApi.updateConfig(installation.id, config);
      } else {
        await StudioApi.install(studio.studioKey, config);
      }
      setDialogOpen(false);
      onChanged();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.studio.loadError);
    } finally {
      setSubmitting(false);
    }
  };

  const act = async (action: () => Promise<unknown>) => {
    setSubmitting(true);
    setError(null);
    try {
      await action();
      onChanged();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.studio.loadError);
    } finally {
      setSubmitting(false);
    }
  };

  if (studio.locked) {
    return <StudioLockNotice reason={studio.lockedReason} packKey={studio.packKey} />;
  }

  if (!studio.latestVersion) {
    return null;
  }

  if (studio.kind === "TOOLKIT") {
    return (
      <Badge tone="success" size="md" className="self-start">
        <CheckIcon size={13} />
        {t.studio.included}
      </Badge>
    );
  }

  if (!installation && !hasSettings) {
    // Nothing to ask: the run form below is the action, and the first run installs it.
    return null;
  }

  return (
    <div className="flex flex-col gap-3">
      <InstallDialog
        open={isDialogOpen}
        configSchema={studio.configSchema}
        initialConfig={installation?.config}
        isSubmitting={isSubmitting}
        onSubmit={(config) => void submit(config)}
        onCancel={() => setDialogOpen(false)}
      />
      <div className="flex flex-wrap items-center gap-2">
        {installation ? (
          <>
            {hasSettings && (
              <Button variant="secondary" size="sm" onClick={() => setDialogOpen(true)} disabled={isSubmitting}>
                {t.studio.configure}
              </Button>
            )}
            {installation.status === "UPGRADE_AVAILABLE" && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => void act(() => StudioApi.upgrade(installation.id))}
                disabled={isSubmitting}
              >
                {t.studio.upgradeNow}
              </Button>
            )}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => void act(() => StudioApi.uninstall(installation.id))}
              disabled={isSubmitting}
            >
              {t.studio.uninstall}
            </Button>
          </>
        ) : (
          hasSettings && (
            <>
              <Button variant="primary" size="md" onClick={() => setDialogOpen(true)} loading={isSubmitting}>
                {t.studio.setUp}
              </Button>
              <span className="text-xs text-fg-secondary">{t.studio.setUpHint}</span>
            </>
          )
        )}
      </div>

      {installation?.status === "UPGRADE_AVAILABLE" && (
        <p className="text-xs text-fg-accent">{t.studio.upgradeAvailable}</p>
      )}

      {error && (
        <p role="alert" className="text-xs text-fg-danger">
          {error}
        </p>
      )}
    </div>
  );
}
