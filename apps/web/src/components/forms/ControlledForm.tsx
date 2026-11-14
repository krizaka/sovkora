"use client";

import * as React from "react";
import { z } from "zod";
import { createKrizakaForm } from "@krizaka/ui/form";

/** A form whose values the screen's own hook already holds: it brings the form's submit, not a second copy of the values. */
const controlled = createKrizakaForm({ schema: z.object({}), defaultValues: {} });

/** The form element of a screen whose fields are driven by its hook: `onSubmit` runs when the form is sent. */
export function ControlledForm({
  id,
  onSubmit,
  className,
  children,
}: Readonly<{ id?: string; onSubmit: () => void | Promise<void>; className?: string; children: React.ReactNode }>) {
  const form = controlled.useForm({ onSubmit: () => onSubmit() });
  return (
    <form.Form id={id} className={className}>
      {children}
    </form.Form>
  );
}
