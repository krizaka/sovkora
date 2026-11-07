/**
 * @file translations.jobsTable.types.ts
 * @description The shape of the Jobs table dictionary (translations.jobsTable.ts), apart so the dictionary types do not import it back.
 */

import type { FactId } from "@/core/types/jobFacts.types";

export interface JobsTableDictionary {
  caption: string;
  columns: {
    preview: string;
    task: string;
    model: string;
    where: string;
    status: string;
    time: string;
    credits: string;
    created: string;
    actions: string;
  };
  status: {
    PENDING: string;
    PROCESSING: string;
    COMPLETED: string;
    FAILED: string;
    CANCELLED: string;
    DELETED: string;
  };
  sections: {
    output: string;
    generation: string;
    hardware: string;
    timing: string;
    other: string;
    prompt: string;
    error: string;
  };
  facts: Record<FactId, string>;
  actions: {
    open: string;
    payload: string;
    download: string;
    copyId: string;
    copied: string;
    delete: string;
    copyError: string;
  };
  modelAuto: string;
  whereUnknown: string;
  running: string;
  empty: string;
  /** `{n}` */
  results: string;
  resultsOne: string;
  /** `{name}` */
  showMore: string;
  /** `{name}` */
  expandRow: string;
  details: string;
  filters: string;
  clearFilters: string;
  /** `{label}` */
  removeFilter: string;
  /** `{header}` `{value}` */
  activeFilter: string;
  /** `{header}` */
  search: string;
  loading: string;
}
