/**
 * @file translations.runProgress.types.ts
 * @description The shape of the run-progress dictionary (translations.runProgress.ts): what a running Studio step says
 * it is doing, the honest time left, leaving the page, cancelling for real, and deleting a result.
 */

export interface RunProgressDictionary {
  phases: { queued: string; loading: string; generating: string; encoding: string; uploading: string };
  /** `{step}` `{steps}` */
  stepOf: string;
  /** `{time}` */
  elapsed: string;
  /** `{time}` */
  overdue: string;
  /** `{n}` */
  basis: string;
  noEstimate: string;
  cancelRun: string;
  cancelConfirm: string;
  cancelling: string;
  cancelFailed: string;
  /** `{name}` */
  cancelled: string;
  inProgress: { title: string; empty: string; open: string; since: string };
  deleteDialog: {
    /** `{name}` */
    title: string;
    description: string;
    confirm: string;
    cancel: string;
    /** `{name}` */
    done: string;
    undo: string;
    /** `{name}` `{reason}` */
    failed: string;
    running: string;
  };
}
