import * as React from "react";
import { Field, Input, Select, Textarea } from "@krizaka/ui/field";

import { SchemaField } from "@/services/interception.api";

/**
 * Properties required by the {@link InterceptionFormField} component.
 */
interface InterceptionFormFieldProps {
  /** The field meta schema definition. */
  field: SchemaField;
  /** Active input control value state. */
  value: string;
  /** Callback fired when control value changes. */
  onChange: (value: string) => void;
  /** The control's DOM id, so its label names it. */
  id?: string;
  /** Flag disabling the form element controls. */
  disabled?: boolean;
  /** Active locale language code identifier. */
  locale: string;
}

/**
 * InterceptionFormField renders a single form control based on the SchemaField definition.
 *
 * @param props - Component React properties.
 * @param props.field - The field meta schema definition.
 * @param props.value - Active input control value state.
 * @param props.onChange - Callback fired when control value changes.
 * @param props.id - The control's DOM id, so its label names it.
 * @param props.disabled - Flag disabling the form element controls.
 * @param props.locale - Active locale language code identifier.
 * @returns The React component representing a field control.
 */
export function InterceptionFormField({
  field,
  value,
  onChange,
  id,
  disabled = false,
  locale,
}: Readonly<InterceptionFormFieldProps>) {
  const controlId = id ?? `interception-${field.name}`;
  return (
    <Field.Root className="space-y-2">
      <Field.Label htmlFor={controlId} className="flex items-center justify-between">
        <span>{field.label}</span>
        {field.required && (
          <span className="text-xs text-danger font-normal">{locale === "fr" ? "* requis" : "* required"}</span>
        )}
      </Field.Label>

      {field.type === "text" && (
        <Input
          id={controlId}
          type="text"
          placeholder={field.placeholder}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          className="w-full bg-surface-1/30"
        />
      )}

      {field.type === "textarea" && (
        <Textarea
          id={controlId}
          placeholder={field.placeholder}
          rows={4}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          className="bg-surface-1/30"
        />
      )}

      {field.type === "select" && (
        <Select
          id={controlId}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          className="bg-surface-1/30"
        >
          {field.options?.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </Select>
      )}
    </Field.Root>
  );
}
