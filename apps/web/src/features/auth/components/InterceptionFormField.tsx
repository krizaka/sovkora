import * as React from "react";
import { Input } from "@krizaka/orazaka-design-system";

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
 * @param props.disabled - Flag disabling the form element controls.
 * @param props.locale - Active locale language code identifier.
 * @returns The React component representing a field control.
 */
export function InterceptionFormField({
  field,
  value,
  onChange,
  disabled = false,
  locale,
}: Readonly<InterceptionFormFieldProps>) {
  return (
    <div className="space-y-2">
      <label className="text-sm font-medium text-fg-secondary flex items-center justify-between">
        <span>{field.label}</span>
        {field.required && (
          <span className="text-xs text-danger font-normal">{locale === "fr" ? "* requis" : "* required"}</span>
        )}
      </label>

      {field.type === "text" && (
        <Input
          type="text"
          placeholder={field.placeholder}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          className="w-full bg-surface-1/30"
        />
      )}

      {field.type === "textarea" && (
        <textarea
          placeholder={field.placeholder}
          rows={4}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          className="flex w-full rounded-xl border border-border-subtle/60 bg-surface-1/30 px-3 py-2 text-sm transition-all duration-200 placeholder:text-fg-muted focus:outline-none focus:ring-2 focus:ring-border-subtle disabled:cursor-not-allowed disabled:opacity-50 focus:ring-success focus-visible:ring-border-subtle text-fg"
        />
      )}

      {field.type === "select" && (
        <select
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          className="flex h-10 w-full rounded-xl border border-border-subtle/60 bg-surface-1/30 px-3 py-2 text-sm transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-border-subtle disabled:cursor-not-allowed disabled:opacity-50 focus:ring-success focus-visible:ring-border-subtle text-fg"
        >
          {field.options?.map((opt) => (
            <option key={opt.value} value={opt.value} className="bg-surface-0 text-fg">
              {opt.label}
            </option>
          ))}
        </select>
      )}
    </div>
  );
}
