"use client";

import * as React from "react";
import { z } from "zod";
import { Button } from "@krizaka/ui/button";
import { Dialog } from "@krizaka/ui/dialog";
import { createKrizakaForm } from "@krizaka/ui/form";
import { fill } from "@/core/context/fill";
import { LawAdminApi } from "@/console/services/law.admin.api";
import type { RuleProposal } from "@/console/services/law.schemas";
import { asRefusal } from "@/console/features/law/hooks/useLawLoad";
import { useLawCopy } from "@/console/features/law/hooks/useLawCopy";
import { canApprove, canEdit, canReject, hasText, type Notice } from "@/console/features/law/utils/law";
import { RuleEditor } from "@/console/features/law/components/RuleEditor";

const approveForm = createKrizakaForm({ schema: z.object({ note: z.string() }), defaultValues: { note: "" } });
// The reason as typed is what is sent: the check reads the original, the server refuses a blank one as well.
const rejectForm = createKrizakaForm({
  schema: z.object({ reason: z.string().refine(hasText, "law.reason") }),
  defaultValues: { reason: "" },
});

interface ActionProps {
  proposal: RuleProposal;
  onDone: (notice: Notice) => void;
}

function ApproveForm({ proposal, onDone }: Readonly<ActionProps>) {
  const { t, refusal } = useLawCopy();
  const a = t.actions;
  const form = approveForm.useForm({
    onSubmit: async ({ note }) => {
      try {
        await LawAdminApi.approve(proposal.id, hasText(note) ? note : undefined);
        onDone({ tone: "success", text: fill(a.approved, { ruleId: proposal.ruleId }) });
      } catch (error) {
        onDone({ tone: "danger", text: refusal(asRefusal(error)) });
      }
    },
  });
  return (
    <form.Form aria-label={`${a.approve} ${proposal.ruleId}`} className="flex flex-col gap-2">
      <form.TextArea name="note" label={a.approveNote} rows={2} />
      <form.Submit variant="primary" size="sm">
        {a.approve}
      </form.Submit>
    </form.Form>
  );
}

function RejectForm({ proposal, onDone }: Readonly<ActionProps>) {
  const { t, refusal } = useLawCopy();
  const a = t.actions;
  const words: Record<string, string> = { "law.reason": a.reasonRequired };
  const form = rejectForm.useForm({
    i18n: { t: (key) => words[key] },
    onSubmit: async ({ reason }) => {
      try {
        await LawAdminApi.reject(proposal.id, reason);
        onDone({ tone: "success", text: fill(a.rejected, { ruleId: proposal.ruleId }) });
      } catch (error) {
        onDone({ tone: "danger", text: refusal(asRefusal(error)) });
      }
    },
  });
  return (
    <form.Form aria-label={`${a.reject} ${proposal.ruleId}`} className="flex flex-col gap-2">
      <form.TextArea name="reason" label={a.rejectReason} rows={2} />
      <form.Submit variant="danger" size="sm">
        {a.reject}
      </form.Submit>
    </form.Form>
  );
}

function EditDialog({ proposal, onDone }: Readonly<ActionProps>) {
  const { t } = useLawCopy();
  const a = t.actions;
  const [open, setOpen] = React.useState(false);
  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Button variant="secondary" size="sm" onClick={() => setOpen(true)}>
        {a.edit}
      </Button>
      <Dialog.Content closeLabel={a.cancel}>
        <Dialog.Header>
          <Dialog.Title>{fill(a.editTitle, { ruleId: proposal.ruleId })}</Dialog.Title>
          <Dialog.Description>{a.editLead}</Dialog.Description>
        </Dialog.Header>
        <Dialog.Body>
          {open && (
            <RuleEditor
              proposal={proposal}
              onDone={(notice) => {
                setOpen(false);
                onDone(notice);
              }}
            />
          )}
        </Dialog.Body>
      </Dialog.Content>
    </Dialog.Root>
  );
}

/** What a reviewer does to one proposal — approve (a note), reject (a reason), edit (a new revision) — when it may. */
export function ProposalActions({ proposal, onDone }: Readonly<ActionProps>) {
  const approve = canApprove(proposal);
  const reject = canReject(proposal);
  const edit = canEdit(proposal);
  if (!approve && !reject && !edit) return null;
  return (
    <section className="grid gap-4 border-t border-border-subtle p-3 md:grid-cols-3">
      {approve && <ApproveForm proposal={proposal} onDone={onDone} />}
      {reject && <RejectForm proposal={proposal} onDone={onDone} />}
      {edit && (
        <section className="flex items-start">
          <EditDialog proposal={proposal} onDone={onDone} />
        </section>
      )}
    </section>
  );
}
