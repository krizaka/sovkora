"use client";

import { Card } from "@krizaka/ui/card";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslation } from "@/core/context/LocaleContext";
import { StudioApi } from "@/services/studio.api";
import { RunForm } from "@/features/studio/components/RunForm";
import { ConsentDialog } from "@/features/studio/consent/ConsentDialog";
import { ConsentRequiredError, type ConsentAnswer, type ConsentRequirement } from "@/features/studio/consent/consent";
import { PackKindSchema, type Installation, type Run, type StudioDetail } from "@krizaka/orazaka-shared";

/**
 * Runs the Studio: through its installation, by its key (TOOLKIT), or by installing it first (a VERTICAL never set up).
 *
 * With a `consent` (a wellbeing Studio, after the person agreed) the Studio is installed again with the three
 * declarations, which is also how a statement that changed since is agreed to anew.
 */
async function startFor(
  studio: StudioDetail,
  installation: Installation | undefined,
  inputs: Record<string, unknown>,
  locale: string,
  consent?: ConsentAnswer,
): Promise<Run | null> {
  if (consent) {
    const installed = await StudioApi.install(studio.studioKey, installation?.config ?? {}, consent, locale);
    return installed ? StudioApi.startRun(installed.id, inputs) : null;
  }
  if (installation) return StudioApi.startRun(installation.id, inputs);
  if (studio.kind === PackKindSchema.enum.TOOLKIT) return StudioApi.startStudioRun(studio.studioKey, inputs);
  const installed = await StudioApi.install(studio.studioKey, {}, undefined, locale);
  return installed ? StudioApi.startRun(installed.id, inputs) : null;
}

/** A run held back by the consent step: what to agree to, and the inputs to run once it is. */
interface Pending {
  requirement: ConsentRequirement;
  inputs: Record<string, unknown>;
}

interface StudioRunPanelProps {
  studio: StudioDetail;
  /** Absent for a TOOLKIT Studio, whose installation is derived and has no id (ADR-061). */
  installation?: Installation;
}

/**
 * The run form for an installed Studio, and the navigation to its live run.
 *
 * A started run redirects to its own screen rather than rendering progress inline:
 * a run outlives this page, and a user who navigates away must be able to come back
 * to it by URL.
 *
 * A VERTICAL Studio nobody has set up yet is installed by its first run (with its settings' defaults, which for most
 * Studios is nothing at all): the Install button was a step that posted an empty form, so it is gone. A TOOLKIT
 * Studio runs by its key, with no installation (ADR-061).
 *
 * A wellbeing (REGULATED) Studio answers the install with 451 and the statement to agree to: nothing runs, the
 * consent step is shown (`ConsentDialog`), and the inputs typed so far are kept to run as soon as it is given.
 */
export function StudioRunPanel({ studio, installation }: Readonly<StudioRunPanelProps>) {
  const { t, locale } = useTranslation();
  const router = useRouter();
  const [isSubmitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<Pending | null>(null);
  const [consentError, setConsentError] = useState<string | null>(null);

  const start = async (inputs: Record<string, unknown>, consent?: ConsentAnswer) => {
    setSubmitting(true);
    setError(null);
    setConsentError(null);
    try {
      const run = await startFor(studio, installation, inputs, locale, consent);
      setPending(null);
      if (run) {
        router.push(`/studios/runs/${run.id}`);
      }
    } catch (caught) {
      if (caught instanceof ConsentRequiredError) {
        setPending({ requirement: caught.requirement, inputs });
      } else if (consent) {
        // The service refused the age or the region: the step stays open and says why.
        setConsentError(caught instanceof Error ? caught.message : t.studio.loadError);
      } else {
        setError(caught instanceof Error ? caught.message : t.studio.loadError);
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (installation?.status === "PAUSED" || installation?.status === "REVOKED") {
    return null;
  }

  return (
    <Card.Root>
      <Card.Body padding="lg" className="gap-4">
        <h2 className="text-base font-semibold text-fg">{t.studio.runTitle}</h2>
        <RunForm
          studioKey={studio.studioKey}
          inputSchema={studio.inputSchema}
          estimatedCredits={studio.estimatedCredits}
          isSubmitting={isSubmitting}
          onSubmit={(inputs) => void start(inputs)}
        />
        {error && (
          <p role="alert" className="text-xs text-fg-danger">
            {error}
          </p>
        )}
      </Card.Body>
      {pending && (
        <ConsentDialog
          open
          studioLabel={studio.label}
          requirement={pending.requirement}
          isSubmitting={isSubmitting}
          error={consentError}
          onConfirm={(answer) => void start(pending.inputs, answer)}
          onCancel={() => {
            setPending(null);
            setConsentError(null);
          }}
        />
      )}
    </Card.Root>
  );
}
