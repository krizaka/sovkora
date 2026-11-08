"use client";

import { useState } from "react";
import { Button } from "@krizaka/ui/button";
import { Checkbox } from "@krizaka/ui/checkbox";
import { Dialog } from "@krizaka/ui/dialog";
import { Field, Select } from "@krizaka/ui/field";
import { useTranslation } from "@/core/context/LocaleContext";
import { fill } from "@/core/context/fill";
import { CONSENT_REGIONS, type ConsentAnswer, type ConsentRequirement } from "@/features/studio/consent/consent";

interface ConsentDialogProps {
  open: boolean;
  studioLabel: string;
  requirement: ConsentRequirement;
  isSubmitting: boolean;
  /** Why the service refused the age or the region, when it did. */
  error: string | null;
  onConfirm: (answer: ConsentAnswer) => void;
  onCancel: () => void;
}

/**
 * The consent step of a wellbeing Studio, shown before its first run (ADR-055 section 3, Law 25, AI Act art. 50).
 *
 * What it does and does not do, that it is not medical advice, that the other side is an AI, the crisis line, then
 * the service's statement verbatim, and the three separate declarations the install reads: the age, the region, the
 * consent. Nothing is pre-checked and the button stays off until all three are given. Closing is refused while the
 * choice is being saved, so a click is never left without an answer.
 */
export function ConsentDialog({ open, isSubmitting, onCancel, ...form }: Readonly<ConsentDialogProps>) {
  const { t } = useTranslation();
  return (
    <Dialog.Root open={open} onOpenChange={(next) => !next && !isSubmitting && onCancel()}>
      <Dialog.Content closeLabel={t.consent.close}>
        <Dialog.Header>
          <Dialog.Title>{fill(t.consent.title, { studio: form.studioLabel })}</Dialog.Title>
          <Dialog.Description>{t.consent.lead}</Dialog.Description>
        </Dialog.Header>
        {/* Mounted while open only: every box starts empty each time the step is shown. */}
        <ConsentForm {...form} isSubmitting={isSubmitting} onCancel={onCancel} />
      </Dialog.Content>
    </Dialog.Root>
  );
}

/** The name of a region in the person's language; the code itself for one we have no name for. */
function regionName(names: Record<string, string>, code: string): string {
  return names[code] ?? code;
}

function ConsentForm({
  requirement,
  error,
  isSubmitting,
  onConfirm,
  onCancel,
}: Readonly<Omit<ConsentDialogProps, "open" | "studioLabel">>) {
  const { t } = useTranslation();
  const c = t.consent;
  const [age, setAge] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [region, setRegion] = useState("");
  const ready = age && agreed && region !== "";
  // The regions come from the server with their verified line; the fixed list only stands in when it sent none.
  const regionCodes: readonly string[] = requirement.regions?.map((line) => line.region) ?? CONSENT_REGIONS;
  const line = requirement.regions?.find((candidate) => candidate.region === region);

  return (
    <>
      <Dialog.Body className="flex flex-col gap-4 text-sm text-fg-secondary">
        <div className="grid gap-4 sm:grid-cols-2">
          <section aria-labelledby="consent-does" className="flex flex-col gap-1.5">
            <h3 id="consent-does" className="text-xs font-semibold text-fg">
              {c.doesTitle}
            </h3>
            <ul className="list-disc space-y-1 pl-4">
              {c.does.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          </section>
          <section aria-labelledby="consent-does-not" className="flex flex-col gap-1.5">
            <h3 id="consent-does-not" className="text-xs font-semibold text-fg">
              {c.doesNotTitle}
            </h3>
            <ul className="list-disc space-y-1 pl-4">
              {c.doesNot.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          </section>
        </div>
        <p>{c.aiNotice}</p>
        <p className="rounded-lg border border-border-default bg-surface-1 px-3 py-2 text-fg">{c.crisis}</p>

        <section aria-labelledby="consent-statement" className="flex flex-col gap-1.5">
          <h3 id="consent-statement" className="text-xs font-semibold text-fg">
            {fill(c.statementTitle, { version: requirement.version })}
          </h3>
          <blockquote className="whitespace-pre-line border-l-2 border-border-default pl-3 text-fg">
            {requirement.statement}
          </blockquote>
          <p className="text-xs text-fg-muted">{c.statementNote}</p>
        </section>

        <Checkbox checked={age} onCheckedChange={(next) => setAge(next === true)} disabled={isSubmitting}>
          {c.ageLabel}
        </Checkbox>

        <Field.Root className="gap-1">
          <Field.Label htmlFor="consent-region" className="text-xs font-semibold text-fg">
            {c.regionLabel}
          </Field.Label>
          <Select
            id="consent-region"
            value={region}
            disabled={isSubmitting}
            onChange={(event) => setRegion(event.target.value)}
          >
            <option value="">{c.regionPlaceholder}</option>
            {regionCodes.map((code) => (
              <option key={code} value={code}>
                {regionName(c.regions, code)}
              </option>
            ))}
          </Select>
          <p className="text-xs text-fg-muted">{c.regionHelp}</p>
          {line && (
            // The server's verified line for the region, shown before anything is agreed to.
            <p
              className="rounded-lg border border-border-default bg-surface-1 px-3 py-2 text-xs text-fg"
              data-testid="helpline"
            >
              {fill(c.helpline, { label: line.label, contact: line.contact, availability: line.availability })}{" "}
              <a className="underline" href={line.sourceUrl} target="_blank" rel="noreferrer">
                {c.helplineSource}
              </a>
            </p>
          )}
        </Field.Root>

        <Checkbox checked={agreed} onCheckedChange={(next) => setAgreed(next === true)} disabled={isSubmitting}>
          {c.consentLabel}
        </Checkbox>

        {error && (
          <p role="alert" className="text-xs text-fg-danger">
            {c.refused} {error}
          </p>
        )}
      </Dialog.Body>
      <Dialog.Footer>
        <Button variant="ghost" size="sm" onClick={onCancel} disabled={isSubmitting}>
          {c.cancel}
        </Button>
        <Button
          size="sm"
          disabled={!ready}
          loading={isSubmitting}
          onClick={() => onConfirm({ consentVersion: requirement.version, ageAttested: age, region })}
        >
          {isSubmitting ? c.agreeing : c.agree}
        </Button>
      </Dialog.Footer>
    </>
  );
}
