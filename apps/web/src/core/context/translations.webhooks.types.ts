/**
 * @file translations.webhooks.types.ts
 * @description The shape of the webhooks settings dictionary (translations.webhooks.ts).
 */

export interface WebhooksDictionary {
  title: string;
  subtitle: string;
  create: string;
  loading: string;
  emptyTitle: string;
  emptyDesc: string;
  caption: string;
  columns: { url: string; events: string; created: string; actions: string };
  events: { completed: string; failed: string; cancelled: string };
  urlLabel: string;
  urlPlaceholder: string;
  urlHelp: string;
  eventsLabel: string;
  createCta: string;
  cancel: string;
  createError: string;
  invalidUrl: string;
  limitReached: string;
  revealTitle: string;
  revealDesc: string;
  copy: string;
  copied: string;
  done: string;
  verifyHint: string;
  actions: { log: string; rotate: string; delete: string };
  rotateConfirm: string;
  deleteConfirm: string;
  rotatedTitle: string;
  logTitle: string;
  logEmpty: string;
  logColumns: { event: string; status: string; attempts: string; answer: string; when: string };
  status: { PENDING: string; DELIVERED: string; DEAD: string };
  close: string;
}
