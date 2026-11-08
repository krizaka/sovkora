/**
 * @file translations.automations.types.ts
 * @description The shape of the automations dictionary (translations.automations.ts). `{x}` are placeholders.
 */

type Weekday = "MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN";

export interface AutomationsDictionary {
  title: string;
  subtitle: string;
  create: string;
  loading: string;
  loadError: string;
  retry: string;
  emptyTitle: string;
  emptyDesc: string;
  /** `{studio}` */
  runsStudio: string;
  enabledLabel: string;
  delete: string;
  deleteConfirm: string;
  showDetails: string;
  hideDetails: string;
  tabs: { triggers: string; runs: string };
  form: {
    title: string;
    desc: string;
    name: string;
    namePlaceholder: string;
    studio: string;
    studioPlaceholder: string;
    inputs: string;
    inputsHint: string;
    inputsInvalid: string;
    nameRequired: string;
    studioRequired: string;
    submit: string;
    cancel: string;
    createError: string;
  };
  triggers: {
    empty: string;
    addWebhook: string;
    addSchedule: string;
    webhook: string;
    schedule: string;
    /** `{hint}` */
    secretHint: string;
    copyUrl: string;
    copied: string;
    rotate: string;
    rotateConfirm: string;
    remove: string;
    removeConfirm: string;
    enable: string;
    disable: string;
    /** `{days}`, `{time}`, `{zone}` */
    scheduleSummary: string;
    everyDay: string;
    /** `{when}` */
    nextRun: string;
    /** `{when}` */
    lastRun: string;
    error: string;
  };
  scheduleForm: {
    title: string;
    desc: string;
    time: string;
    zone: string;
    days: string;
    catchUp: string;
    catchOnce: string;
    catchSkip: string;
    invalidTime: string;
    invalidZone: string;
    submit: string;
    cancel: string;
  };
  secret: {
    title: string;
    desc: string;
    urlLabel: string;
    secretLabel: string;
    copy: string;
    copied: string;
    done: string;
    close: string;
    verify: string;
  };
  runs: {
    empty: string;
    caption: string;
    columns: { status: string; trigger: string; started: string; duration: string; detail: string };
    status: Record<"queued" | "running" | "succeeded" | "failed" | "skipped_budget", string>;
    trigger: Record<"webhook" | "schedule", string>;
    skippedBudget: string;
    studioRun: string;
  };
  days: Record<Weekday, string>;
}
