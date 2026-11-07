import type { InputHTMLAttributes } from "react";
import { Field, Input } from "@krizaka/ui/field";

export interface BillingFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  /** What the field is for. */
  label: string;
}

/**
 * A labelled input of the billing console: `Field.Root`, `Field.Label` and `Input` from @krizaka/ui/field.
 *
 * The label association lives here rather than being re-typed at every call site — a form this size would otherwise
 * accumulate a dozen chances to forget `htmlFor`.
 */
export function BillingField({ label, id, ...props }: BillingFieldProps) {
  const fieldId = id ?? `field-${label.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`;
  return (
    <Field.Root>
      <Field.Label htmlFor={fieldId}>{label}</Field.Label>
      <Input id={fieldId} {...props} />
    </Field.Root>
  );
}
