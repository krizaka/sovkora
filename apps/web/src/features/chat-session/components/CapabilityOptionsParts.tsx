import * as React from "react";
import { cn } from "@krizaka/ui/cn";
import { Field, Select } from "@krizaka/ui/field";

export interface ModelOption {
  value: string;
  label: string;
  /** Host hardware compatibility from the catalog; incompatible models are greyed + disabled. */
  compatible: boolean;
  reason?: string | null;
  /** Catalog description, surfaced under the picker so the user knows what the model does. */
  description?: string | null;
  /** Image-to-video models need a seed image; the UI surfaces a reference-image affordance. */
  requiresReferenceImage?: boolean;
}

/**
 * One option of the composer (model, voice, size, duration): a labelled native select — `Field.Root`, `Field.Label`
 * and `Select` from @krizaka/ui/field, at the composer's compact size.
 */
export function CapabilityField({
  label,
  className,
  ...props
}: Readonly<{ label: string } & React.ComponentProps<typeof Select>>) {
  const id = React.useId();
  return (
    <Field.Root className="min-w-[120px] flex-1 gap-1">
      <Field.Label htmlFor={id} className="text-[10px] text-fg-muted">
        {label}
      </Field.Label>
      <Select id={id} className={cn("h-8 cursor-pointer pl-2.5 text-xs", className)} {...props} />
    </Field.Root>
  );
}
