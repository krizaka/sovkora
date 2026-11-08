"use client";

import { useMemo } from "react";
import { formatDate } from "@krizaka/intl/date";
import type { DataTableLabels } from "@krizaka/ui/data-table";
import { fill } from "@/core/context/fill";
import { lawCopy, type LawCopy } from "@/console/core/copy/law";
import { CONSOLE_INTL, useConsoleLocale, type ConsoleLocale } from "@/console/core/providers/ConsoleLocale";
import type { AdminRefusal } from "@/console/services/platform.admin.api";
import { refusalKey } from "@/console/features/law/utils/law";

export interface LawWords {
  t: LawCopy;
  locale: ConsoleLocale;
  /** A date (`YYYY-MM-DD`) in the screen's language; an instant also with its time, both in UTC as the API writes them. */
  date: (iso: string, withTime?: boolean) => string;
  /** The words of a refusal: its Problem code, else its status. */
  refusal: (refusal: AdminRefusal) => string;
  /** The words of every table of the screen. */
  table: Partial<DataTableLabels>;
}

function tableLabels(w: LawCopy["table"]): Partial<DataTableLabels> {
  return {
    empty: w.empty,
    loading: w.loading,
    details: w.details,
    expandRow: (name) => fill(w.expandRow, { name }),
    showMore: (name) => fill(w.showMore, { name }),
    results: (count) => fill(w.results, { count }),
    pagination: {
      nav: w.nav,
      previous: w.previous,
      next: w.next,
      page: (page) => fill(w.page, { page }),
      compact: (page, count) => fill(w.compact, { page, count }),
      pageSize: w.pageSize,
    },
  };
}

/** The words of Law → Proposals in the console's language. */
export function useLawCopy(): LawWords {
  const locale = useConsoleLocale();
  return useMemo(() => {
    const t = lawCopy[locale];
    const intl = CONSOLE_INTL[locale];
    return {
      t,
      locale,
      date: (iso, withTime = false) =>
        formatDate(iso, { locale: intl, style: "medium", timeStyle: withTime ? "short" : undefined, timeZone: "UTC" }),
      refusal: (r) => t.refusals[refusalKey(r.code, r.status, t.refusals)],
      table: tableLabels(t.table),
    };
  }, [locale]);
}
