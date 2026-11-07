"use client";

import * as React from "react";
import { enUS, fr } from "date-fns/locale";
import { CheckIcon, CopyIcon, DownloadIcon, EyeIcon, TrashIcon } from "@krizaka/icons";
import { Badge } from "@krizaka/ui/badge";
import { IconButton } from "@krizaka/ui/button";
import { DataTable, DataTableFilters, type DataTableColumn } from "@krizaka/ui/data-table";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { jobLabel, jobMediaUrl } from "@/core/jobs/jobMedia";
import { JOB_STATUS } from "@/core/constants/http.constants";
import {
  formatDuration,
  jobFacts,
  modelOf,
  promptOf,
  studioOf,
  wallTimeMs,
  whereRan,
} from "@/features/jobs/utils/jobFacts";
import { timeAgoCell } from "@/core/components/timeAgo";
import type { Job } from "@/core/types/jobs.types";
import { JobDetail } from "./JobDetail";
import { MODEL_NAMES } from "./modelNames";
import { JobThumb } from "./JobThumb";
import { PAGE_SIZES, TONE, type JobsTableProps } from "./jobsTableShape";

export type { JobsTableProps } from "./jobsTableShape";

const previewCell = (j: Job) => <JobThumb job={j} />;

const modelCell = (name: string) => <span className="font-medium">{name}</span>;

/**
 * The tasks, one line each, on the kit's responsive DataTable (cards on a phone): a preview, the task and its Studio, the
 * model (always a name), where it ran, the status, the real wall time, and what leaves the row as the table narrows goes to
 * its detail — grouped facts in words and units. Filters (search, status, model, where), sort, and the page the server sent.
 */
export function JobsTable({
  jobs,
  loading,
  totalElements,
  currentPage,
  pageSize,
  onPageChange,
  onPageSizeChange,
  onOpenModal,
  copiedId,
  onCopy,
  onDelete,
}: Readonly<JobsTableProps>) {
  const { t, locale } = useTranslation();
  const d = t.jobsTable;
  const dateLocale = locale === "fr" ? fr : enUS;
  const hasCredits = jobs.some((j) => jobFacts(j).some((f) => f.id === "credits"));

  const columns = React.useMemo<DataTableColumn<Job>[]>(() => {
    const model = (j: Job) => modelOf(j, MODEL_NAMES) ?? d.modelAuto;
    const where = (j: Job) => whereRan(j) ?? d.whereUnknown;
    return [
      {
        id: "preview",
        header: d.columns.preview,
        priority: 1,
        hideable: false,
        width: 84,
        cell: previewCell,
      },
      {
        id: "task",
        header: d.columns.task,
        type: "title",
        filter: "text",
        sortable: true,
        // What the search looks into: the kind, the id, the prompt and the model.
        value: (j) => [jobLabel(t, j), j.id, promptOf(j) ?? "", model(j)].join(" "),
        cell: (j) => jobLabel(t, j),
        subtitle: (j) => studioOf(j) ?? j.id.slice(0, 8),
      },
      {
        id: "model",
        header: d.columns.model,
        priority: 2,
        filter: "facet",
        sortable: true,
        type: "text",
        nowrap: true,
        value: model,
        cell: (j) => modelCell(model(j)),
      },
      {
        id: "where",
        header: d.columns.where,
        priority: 3,
        filter: "facet",
        sortable: true,
        type: "text",
        nowrap: true,
        value: where,
      },
      {
        id: "status",
        header: d.columns.status,
        type: "status",
        filter: "facet",
        sortable: true,
        value: (j) => d.status[j.status],
        cell: (j) => (
          <Badge tone={TONE[j.status]} size="sm" dot={j.status === JOB_STATUS.PROCESSING}>
            {d.status[j.status]}
          </Badge>
        ),
      },
      {
        id: "time",
        header: d.columns.time,
        priority: 2,
        type: "number",
        align: "end",
        sortable: true,
        value: (j) => wallTimeMs(j),
        cell: (j) =>
          j.status === JOB_STATUS.PENDING || j.status === JOB_STATUS.PROCESSING
            ? d.running
            : formatDuration(wallTimeMs(j) ?? 0),
      },
      {
        id: "credits",
        header: d.columns.credits,
        priority: 4,
        type: "number",
        align: "end",
        value: (j) => Number(jobFacts(j).find((f) => f.id === "credits")?.value ?? NaN) || null,
      },
      {
        id: "created",
        header: d.columns.created,
        priority: 3,
        type: "date",
        sortable: true,
        value: (j) => new Date(j.createdAt),
        cell: (j) => timeAgoCell(j.createdAt, dateLocale),
      },
      {
        id: "actions",
        header: d.columns.actions,
        headerHidden: true,
        align: "end",
        hideable: false,
        card: "hidden",
        cell: (j) => {
          const url = jobMediaUrl(j);
          return (
            <span className="inline-flex items-center gap-0.5">
              <IconButton
                variant="ghost"
                size="sm"
                label={j.status === JOB_STATUS.COMPLETED ? d.actions.open : d.actions.payload}
                onClick={() => onOpenModal(j, j.status === JOB_STATUS.COMPLETED ? "result" : "payload")}
              >
                <EyeIcon size={15} />
              </IconButton>
              {url && (
                <IconButton variant="ghost" size="sm" label={d.actions.download} asChild>
                  <a href={url} download>
                    <DownloadIcon size={15} />
                  </a>
                </IconButton>
              )}
              <IconButton
                variant="ghost"
                size="sm"
                label={copiedId === j.id ? d.actions.copied : d.actions.copyId}
                onClick={() => onCopy(j.id, j.id)}
              >
                {copiedId === j.id ? <CheckIcon size={15} /> : <CopyIcon size={15} />}
              </IconButton>
              {onDelete && (
                <IconButton variant="ghost" size="sm" label={d.actions.delete} onClick={() => onDelete(j)}>
                  <TrashIcon size={15} />
                </IconButton>
              )}
            </span>
          );
        },
      },
    ];
  }, [d, t, dateLocale, copiedId, onOpenModal, onCopy, onDelete]);

  return (
    <DataTable
      caption={d.caption}
      rows={jobs}
      columns={columns}
      getRowId={(j) => j.id}
      rowLabel={(j) => jobLabel(t, j)}
      loading={loading && jobs.length === 0}
      columnVisibility={{ credits: hasCredits }}
      defaultSort={{ column: "created", direction: "descending" }}
      renderDetail={(j) => <JobDetail job={j} copiedId={copiedId} onCopy={onCopy} />}
      toolbar={<DataTableFilters />}
      manual={{ pagination: true }}
      rowCount={totalElements}
      page={currentPage + 1}
      onPageChange={(p) => onPageChange(p - 1)}
      pageSize={pageSize}
      pageSizeOptions={PAGE_SIZES}
      onPageSizeChange={onPageSizeChange}
      empty={<p className="p-6 text-center text-fg-secondary">{d.empty}</p>}
      labels={{
        loading: d.loading,
        empty: d.empty,
        details: d.details,
        expandRow: (name) => fill(d.expandRow, { name }),
        showMore: (name) => fill(d.showMore, { name }),
        results: (n) => (n === 1 ? d.resultsOne : fill(d.results, { n })),
        filters: d.filters,
        clearFilters: d.clearFilters,
        removeFilter: (label) => fill(d.removeFilter, { label }),
        activeFilter: (header, value) => fill(d.activeFilter, { header, value }),
        search: (header) => fill(d.search, { header }),
        pagination: {
          nav: t.insights.pagination.nav,
          previous: t.jobs.previous,
          next: t.jobs.next,
          page: (page) => fill(t.insights.pagination.page, { page }),
          compact: (page, pageCount) => fill(t.insights.pagination.compact, { page, count: pageCount }),
          pageSize: t.insights.pagination.pageSize,
        },
      }}
    />
  );
}
