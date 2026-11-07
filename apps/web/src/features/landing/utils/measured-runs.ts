/**
 * @file measured-runs.ts
 * @description The figures of the home page's proof band, read from the published « Measured runs » page
 * (`content/docs/measured-runs.md`, generated from the platform's run records): how many runs were measured and how
 * many succeeded, over how many models, on which day. Nothing here is typed by hand — a new measurement moves the figures.
 */

export interface MeasuredRuns {
  /** Runs that finished with an output. */
  ok: number;
  /** Every run recorded, failures included. */
  all: number;
  /** Models with at least one run. */
  models: number;
  /** The day of the measurement (`YYYY-MM-DD`), when the page says it. */
  day: string | null;
}

/** `| **Model** (kind) | where | 21 / 21 | … |` — the third cell is « ok / all ». */
const ROW = /^\|[^|]+\|[^|]+\|\s*(\d+)\s*\/\s*(\d+)\s*\|/;

export function measuredRuns(markdown: string): MeasuredRuns {
  let ok = 0;
  let all = 0;
  let models = 0;
  for (const line of markdown.split("\n")) {
    const row = ROW.exec(line);
    if (!row) continue;
    ok += Number(row[1]);
    all += Number(row[2]);
    models += 1;
  }
  const day = /measured on \*\*(\d{4}-\d{2}-\d{2})\*\*/.exec(markdown)?.[1] ?? null;
  return { ok, all, models, day };
}
