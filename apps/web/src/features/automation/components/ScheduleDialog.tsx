"use client";

import * as React from "react";
import { z } from "zod";
import { Dialog } from "@krizaka/ui/dialog";
import { createKrizakaForm } from "@krizaka/ui/form";
import { useTranslation } from "@/core/context/LocaleContext";
import type { NewTrigger } from "@/services/automation.api";

const WEEKDAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"] as const;

/** A region id the platform's time zone data knows (`America/Toronto`), never an offset. */
function isRegionZone(zone: string): boolean {
  try {
    new Intl.DateTimeFormat("en", { timeZone: zone });
    return zone.includes("/") || zone === "UTC";
  } catch {
    return false;
  }
}

const DEFAULTS = {
  time: "08:00",
  zone: "UTC",
  MON: true,
  TUE: true,
  WED: true,
  THU: true,
  FRI: true,
  SAT: true,
  SUN: true,
  catchUp: "once" as const,
};

const schedule = createKrizakaForm({
  schema: z.object({
    time: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, "automations.time"),
    zone: z.string().refine(isRegionZone, "automations.zone"),
    MON: z.boolean(),
    TUE: z.boolean(),
    WED: z.boolean(),
    THU: z.boolean(),
    FRI: z.boolean(),
    SAT: z.boolean(),
    SUN: z.boolean(),
    catchUp: z.enum(["once", "skip"]),
  }),
  defaultValues: DEFAULTS,
});

function ScheduleForm({ onSubmit }: Readonly<{ onSubmit: (trigger: NewTrigger) => Promise<void> }>) {
  const { t } = useTranslation();
  const f = t.automations.scheduleForm;
  const words: Record<string, string> = { "automations.time": f.invalidTime, "automations.zone": f.invalidZone };
  const browserZone = React.useMemo(() => Intl.DateTimeFormat().resolvedOptions().timeZone, []);
  const form = schedule.useForm({
    i18n: { t: (key) => words[key] },
    defaultValues: { ...DEFAULTS, zone: isRegionZone(browserZone) ? browserZone : "UTC" },
    onSubmit: async (values) => {
      const days = WEEKDAYS.filter((day) => values[day]);
      await onSubmit({ kind: "schedule", time: values.time, zone: values.zone, days, catchUp: values.catchUp });
    },
  });
  return (
    <form.Form aria-label={f.title}>
      <form.TextField name="time" label={f.time} placeholder="08:00" inputMode="numeric" autoComplete="off" />
      <form.TextField name="zone" label={f.zone} autoComplete="off" />
      <fieldset className="space-y-2">
        <legend className="text-xs font-medium text-fg-secondary">{f.days}</legend>
        <div className="flex flex-wrap gap-x-4 gap-y-2">
          {WEEKDAYS.map((day) => (
            <form.Checkbox key={day} name={day} label={t.automations.days[day]} />
          ))}
        </div>
      </fieldset>
      <form.RadioGroup
        name="catchUp"
        label={f.catchUp}
        options={[
          { value: "once", label: f.catchOnce },
          { value: "skip", label: f.catchSkip },
        ]}
      />
      <form.FormError />
      <form.Submit>{f.submit}</form.Submit>
    </form.Form>
  );
}

/** The « add a schedule » dialog: a time of day, a time zone, the weekdays and what to do with a missed slot. */
export function ScheduleDialog({
  open,
  onClose,
  onSubmit,
}: Readonly<{ open: boolean; onClose: () => void; onSubmit: (trigger: NewTrigger) => Promise<void> }>) {
  const { t } = useTranslation();
  const f = t.automations.scheduleForm;
  return (
    <Dialog.Root open={open} onOpenChange={(next) => !next && onClose()}>
      <Dialog.Content closeLabel={f.cancel}>
        <Dialog.Header>
          <Dialog.Title>{f.title}</Dialog.Title>
          <Dialog.Description>{f.desc}</Dialog.Description>
        </Dialog.Header>
        <Dialog.Body>
          {open && (
            <ScheduleForm
              onSubmit={async (trigger) => {
                await onSubmit(trigger);
                onClose();
              }}
            />
          )}
        </Dialog.Body>
      </Dialog.Content>
    </Dialog.Root>
  );
}
