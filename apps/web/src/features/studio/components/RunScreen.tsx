"use client";

import Link from "next/link";
import { format } from "date-fns";
import { BackIcon } from "@krizaka/icons";
import { useState } from "react";
import { Button } from "@krizaka/ui/button";
import { ConfirmButton } from "@krizaka/ui/confirm-button";
import { toast } from "@krizaka/ui/toast";
import { Card } from "@krizaka/ui/card";
import { Mascot, type MascotMood } from "@krizaka/ui/mascots";
import { isTerminalRun, type RunStatus } from "@krizaka/orazaka-shared";
import { birdFor } from "@/components/mascot/birdFor";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { StudioLoadError, StudioLoading } from "./StudioAsyncState";
import { useStudioRun } from "@/features/studio/hooks/useStudioRun";
import { useStudioDetail } from "@/features/studio/hooks/useStudioDetail";
import { RunTimeline } from "@/features/studio/components/RunTimeline";
import { RunOutputs } from "@/features/studio/components/RunOutputs";
import { StudioApi } from "@/services/studio.api";

interface RunScreenProps {
  runId: string;
}

/** The mascot's mood for each run state: a run is the long wait, the result or the failure a mascot is for. */
const MOOD: Record<RunStatus, MascotMood> = {
  PENDING_APPROVAL: "welcome",
  RUNNING: "thinking",
  AWAITING_INPUT: "welcome",
  SUCCEEDED: "success",
  FAILED: "error",
  CANCELLED: "empty",
  COMPENSATING: "error",
};

/**
 * The live run screen: state, per-step timeline, results.
 *
 * The Studio's bird says where the run stands (thinking while it runs, a success when it is
 * done, worried when it failed) beside the progress of its steps — one mascot, at the moment
 * the person waits for the work.
 *
 * The approve and cancel actions live here rather than on the timeline because they
 * act on the run, not on a node — and a cancel offered next to a step invites the
 * user to think they are cancelling only that step.
 */
// A step that no longer runs (done, or skipped by a condition) counts as progress.
const FINISHED_STEP_STATUSES: readonly string[] = ["SUCCEEDED", "SKIPPED"];

export function RunScreen({ runId }: Readonly<RunScreenProps>) {
  const { t } = useTranslation();
  const { run, isLoading, hasError, reload } = useStudioRun(runId);
  const [stopping, setStopping] = useState(false);
  const { studio } = useStudioDetail(run?.studioKey ?? "");

  if (isLoading) {
    return <StudioLoading />;
  }

  if (hasError || !run) {
    return <StudioLoadError />;
  }

  const headline: Record<RunStatus, string> = {
    PENDING_APPROVAL: t.studio.runAwaitingInput,
    RUNNING: t.studio.runRunning,
    AWAITING_INPUT: t.studio.runAwaitingInput,
    SUCCEEDED: t.studio.runSucceeded,
    FAILED: t.studio.runFailed,
    CANCELLED: t.studio.runCancelled,
    COMPENSATING: t.studio.runFailed,
  };
  const body: Partial<Record<RunStatus, string>> = {
    RUNNING: t.showcase.studio.runRunningBody,
    SUCCEEDED: t.showcase.studio.runSucceededBody,
    FAILED: t.showcase.studio.runFailedBody,
    COMPENSATING: t.showcase.studio.runFailedBody,
  };
  const done = run.steps.filter((step) => FINISHED_STEP_STATUSES.includes(step.status)).length;
  const total = run.steps.length;

  const act = async (action: () => Promise<void>) => {
    await action();
    reload();
  };

  /** A real stop: the server tells the worker, releases the hold; the screen says so or says it failed. */
  const stop = async () => {
    setStopping(true);
    try {
      await StudioApi.cancelRun(run.id);
      toast.info(fill(t.runProgress.cancelled, { name: studio?.label ?? run.studioKey }));
    } catch {
      toast.error(t.runProgress.cancelFailed);
    } finally {
      setStopping(false);
      reload();
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <Button asChild variant="ghost" size="sm" className="self-start">
        <Link href={`/studios/${run.studioKey}`}>
          <BackIcon size={15} />
          {studio?.label ?? t.studio.back}
        </Link>
      </Button>

      <Card.Root>
        <Card.Body padding="lg" className="flex-row flex-wrap items-center gap-5">
          <Mascot bird={birdFor(run.studioKey)} mood={MOOD[run.status]} size={112} />
          <div aria-live="polite" className="flex min-w-0 flex-1 flex-col gap-1.5">
            <h1 className="font-display text-xl font-bold tracking-[-0.02em] text-fg">{headline[run.status]}</h1>
            {body[run.status] && <p className="text-sm text-fg-secondary">{body[run.status]}</p>}
            <span className="font-mono text-[11px] text-fg-muted">
              {studio?.label ?? run.studioKey} · {run.blueprintVersion} ·{" "}
              {format(new Date(run.startedAt), "dd MMM yyyy HH:mm")}
            </span>
            {run.errorMessage && (
              <p role="alert" className="text-xs text-fg-danger">
                {run.errorMessage}
              </p>
            )}
            {!isTerminalRun(run.status) && total > 1 && (
              <span className="font-mono text-[11px] text-fg-muted">
                {fill(t.showcase.studio.stepsProgress, { done, total })}
              </span>
            )}
          </div>
          {!isTerminalRun(run.status) && (
            <div className="flex items-center gap-2">
              {run.status === "AWAITING_INPUT" && (
                <Button variant="primary" size="sm" onClick={() => void act(() => StudioApi.approveRun(run.id))}>
                  {t.studio.approve}
                </Button>
              )}
              <ConfirmButton
                variant="ghost"
                size="sm"
                disabled={stopping}
                confirmLabel={t.runProgress.cancelConfirm}
                onConfirm={() => void stop()}
              >
                {stopping ? t.runProgress.cancelling : t.studio.cancelRun}
              </ConfirmButton>
            </div>
          )}
        </Card.Body>
      </Card.Root>

      <RunOutputs outputs={run.outputs} />
      <RunTimeline steps={run.steps} />
    </div>
  );
}
