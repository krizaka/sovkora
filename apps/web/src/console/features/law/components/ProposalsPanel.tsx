"use client";

import * as React from "react";
import { Alert } from "@krizaka/ui/alert";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Chip } from "@krizaka/ui/chip";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { Field, Select } from "@krizaka/ui/field";
import { useRouteState } from "@krizaka/ui/router";
import { LawAdminApi } from "@/console/services/law.admin.api";
import { PROPOSAL_STATUSES, type ProposalStatus, type RuleProposal } from "@/console/services/law.schemas";
import { useLawLoad } from "@/console/features/law/hooks/useLawLoad";
import { useLawCopy, type LawWords } from "@/console/features/law/hooks/useLawCopy";
import { kindsOf, orDash, wordOf, type Notice } from "@/console/features/law/utils/law";
import { ProposalDetail } from "@/console/features/law/components/ProposalDetail";
import { ProposalActions } from "@/console/features/law/components/ProposalActions";
import { KindPanel } from "@/console/features/law/components/KindPanel";

const ALL = "all";
const STATUS_FILTERS = [ALL, ...PROPOSAL_STATUSES] as const;
type StatusFilter = (typeof STATUS_FILTERS)[number];

const TONE: Record<ProposalStatus, "neutral" | "accent" | "success" | "warning" | "danger"> = {
  PROPOSED: "accent",
  REJECTED_AUTO: "warning",
  APPROVED: "success",
  REJECTED: "danger",
  PROMOTED: "success",
  SUPERSEDED: "neutral",
};

function queueColumns({ t, date }: LawWords): DataTableColumn<RuleProposal>[] {
  const q = t.queue;
  return [
    {
      id: "ruleId",
      header: q.rule,
      card: "title",
      sortable: true,
      value: (p) => p.ruleId,
      cell: (p) => (
        <span className="flex flex-col">
          <code className="text-xs font-semibold text-fg">{p.ruleId}</code>
          <span className="text-xs text-fg-secondary">{orDash(p.rule.title)}</span>
        </span>
      ),
    },
    { id: "kindId", header: q.kind, sortable: true, cell: (p) => <code className="text-xs">{p.kindId}</code> },
    {
      id: "status",
      header: q.status,
      value: (p) => t.status[p.status],
      cell: (p) => (
        <Badge tone={TONE[p.status]} size="sm">
          {t.status[p.status]}
        </Badge>
      ),
    },
    { id: "severity", header: q.severity, value: (p) => wordOf(t.severity, p.rule.severity) },
    { id: "revision", header: q.revision, align: "end", value: (p) => p.proposalRevision },
    { id: "createdAt", header: q.created, sortable: true, value: (p) => p.createdAt, cell: (p) => date(p.createdAt) },
  ];
}

/** The review queue: by status and by kind (both in the URL), each proposal opening on its law, rule and actions. */
export function ProposalsPanel() {
  const copy = useLawCopy();
  const { t, refusal, table } = copy;
  const q = t.queue;
  const [status] = useRouteState<StatusFilter>("status", { values: STATUS_FILTERS, default: ALL });
  const [kind, setKind] = useRouteState<string>("kind", { default: ALL, history: "replace" });
  const [notice, setNotice] = React.useState<Notice | null>(null);
  const [version, setVersion] = React.useState(0);

  const load = React.useCallback(() => LawAdminApi.proposals(status === ALL ? undefined : status), [status]);
  const queue = useLawLoad<RuleProposal[]>(load, [], version);
  const kinds = React.useMemo(() => kindsOf(queue.data), [queue.data]);
  const rows = React.useMemo(
    () => (kind === ALL ? queue.data : queue.data.filter((p) => p.kindId === kind)),
    [queue.data, kind],
  );
  const decided = React.useCallback((next: Notice) => {
    setNotice(next);
    setVersion((v) => v + 1);
  }, []);

  const columns = React.useMemo(() => queueColumns(copy), [copy]);

  return (
    <section className="flex flex-col gap-4">
      <section className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <Chip.Group
          type="single"
          size="sm"
          required
          label={q.statusFilter}
          routeKey="status"
          routeValues={STATUS_FILTERS}
          defaultValue={ALL}
          className="flex-wrap gap-1.5"
        >
          {STATUS_FILTERS.map((s) => (
            <Chip key={s} value={s}>
              {s === ALL ? q.allStatuses : t.status[s]}
            </Chip>
          ))}
        </Chip.Group>
        <Field.Root className="w-full lg:w-72">
          <Field.Label htmlFor="law-kind">{q.kindFilter}</Field.Label>
          <Select id="law-kind" value={kind} onChange={(event) => setKind(event.target.value)}>
            <option value={ALL}>{q.allKinds}</option>
            {[...new Set([...kinds, ...(kind === ALL ? [] : [kind])])].map((k) => (
              <option key={k} value={k}>
                {k}
              </option>
            ))}
          </Select>
        </Field.Root>
      </section>
      {queue.refusal && !queue.isLoading && (
        <Alert
          tone="warning"
          action={
            <Button variant="secondary" size="sm" onClick={queue.reload}>
              {t.retry}
            </Button>
          }
        >
          {refusal(queue.refusal)}
        </Alert>
      )}
      {notice && (
        <Alert tone={notice.tone} role="status">
          {notice.text}
        </Alert>
      )}
      <DataTable
        caption={q.caption}
        rows={rows}
        columns={columns}
        getRowId={(p) => p.id}
        rowLabel={(p) => p.ruleId}
        loading={queue.isLoading}
        defaultPageSize={20}
        labels={table}
        renderDetail={(p) => (
          <ProposalDetail proposal={p}>
            <ProposalActions proposal={p} onDone={decided} />
          </ProposalDetail>
        )}
      />
      {kind === ALL ? (
        <p className="text-sm text-fg-muted">{t.kind.pick}</p>
      ) : (
        <KindPanel kindId={kind} version={version} onPromoted={() => setVersion((v) => v + 1)} />
      )}
    </section>
  );
}
