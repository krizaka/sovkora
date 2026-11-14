"use client";

import * as React from "react";
import { Field, Input } from "@krizaka/ui/field";
import { useKrizakaField } from "@krizaka/ui/form";

type InputFieldProps = Readonly<
  Omit<React.ComponentProps<typeof Input>, "name" | "id" | "value" | "onChange" | "onBlur" | "aria-describedby"> & {
    /** The field's name in the form's schema. */
    name: string;
    /** The control's DOM id: stable, because journeys and links address it. */
    id: string;
    /** The visible label, passed translated. */
    label: React.ReactNode;
    /** A line under the control, read with it (a link, a help text), kept beside the error. */
    hint?: React.ReactNode;
    /** Marks the control invalid even before the form refuses it (a refusal the screen shows above). */
    invalid?: boolean;
  }
>;

/** A text field of a `@krizaka/ui/form` form that keeps its own DOM id: the form's value, error and blur, any hint node. */
export function InputField({ name, id, label, hint, invalid, ...input }: InputFieldProps) {
  const field = useKrizakaField<string>(name);
  const describedBy = [field.error ? field.errorId : "", hint ? field.hintId : ""].join(" ").trim() || undefined;
  return (
    <Field.Root>
      <Field.Label htmlFor={id}>{label}</Field.Label>
      <Input
        id={id}
        name={name}
        value={field.value ?? ""}
        onChange={(event) => field.setValue(event.target.value)}
        onBlur={field.onBlur}
        invalid={Boolean(field.error) || invalid}
        aria-describedby={describedBy}
        {...input}
      />
      {field.error && <Field.Error id={field.errorId}>{field.error}</Field.Error>}
      {hint && <Field.Hint id={field.hintId}>{hint}</Field.Hint>}
    </Field.Root>
  );
}
