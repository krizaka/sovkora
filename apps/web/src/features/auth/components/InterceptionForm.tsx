"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { useSession } from "next-auth/react";
import { Button } from "@krizaka/orazaka-design-system";
import { Icon } from "@krizaka/orazaka-design-system";
import { useTranslation } from "@/core/context/LocaleContext";
import { InterceptionFormField } from "./InterceptionFormField";
import { useInterceptionSchema, useResolveInterception } from "@/features/auth/hooks/useInterception";
import { Card } from "@krizaka/ui/card";
import { createKrizakaForm } from "@krizaka/ui/form";
import { z } from "zod";
import type { SchemaDescriptor } from "@/services/interception.api";

interface InterceptionFormProps {
  schemaId: string;
  interceptionType?: string;
}

export function InterceptionForm({ schemaId, interceptionType = schemaId }: Readonly<InterceptionFormProps>) {
  const { t } = useTranslation();
  const { schema, isLoading, error } = useInterceptionSchema(schemaId);

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[300px] space-y-4">
        <Icon name="loader" size={32} className="animate-spin text-success" />
        <p className="text-fg-secondary text-sm">{t.interception.loadingSchema}</p>
      </div>
    );
  }

  if (error || !schema) {
    return (
      <Card.Root className="max-w-md mx-auto border-danger/30 bg-danger/5 ">
        <Card.Body padding="lg" className="gap-1.5">
          <div className="flex items-center space-x-2 text-danger">
            <Icon name="error" size={24} />
            <Card.Title className="line-clamp-none text-lg leading-none tracking-tight group-hover:text-fg">
              {t.interception.loadingError}
            </Card.Title>
          </div>
          <Card.Description className="line-clamp-none text-sm">{t.interception.loadingErrorDesc}</Card.Description>
        </Card.Body>
        <Card.Footer className="px-6 pb-6 text-sm">
          <Button variant="outline" onClick={() => globalThis.location.reload()} className="w-full">
            {t.interception.retry}
          </Button>
        </Card.Footer>
      </Card.Root>
    );
  }

  return <InterceptionFormBody schemaId={schemaId} interceptionType={interceptionType} schema={schema} />;
}

/** The loaded schema's form: its fields are the schema's, so the form is defined once per loaded schema. */
function InterceptionFormBody({
  schemaId,
  interceptionType,
  schema,
}: Readonly<{ schemaId: string; interceptionType: string; schema: SchemaDescriptor }>) {
  const router = useRouter();
  const { data: session, update } = useSession();
  const { locale, t } = useTranslation();
  const [errorMsg, setErrorMsg] = React.useState<string | null>(null);

  // The schema's defaults, laid out by field name: the values a person edits.
  const [interception] = React.useState(() =>
    createKrizakaForm({
      schema: z.object({ values: z.record(z.string(), z.string()) }),
      defaultValues: {
        values: Object.fromEntries(schema.fields.map((field) => [field.name, field.defaultValue || ""])),
      },
    }),
  );

  const { resolve, isPending } = useResolveInterception({
    interceptionType,
    schemaId,
    onSuccess: () => {
      if (session?.user) {
        const updated = (session.user.activeInterceptions || []).filter((id) => id !== schemaId);
        update({ activeInterceptions: updated }).then(() => {
          router.push("/");
          router.refresh();
        });
      }
    },
    onError: (msg) => {
      setErrorMsg(msg);
    },
  });

  const form = interception.useForm({
    onSubmit: ({ values }) => {
      setErrorMsg(null);
      const missing = schema.fields.find((f) => f.required && !values[f.name]?.trim());
      if (missing) {
        setErrorMsg(t.interception.fieldRequired(missing.label));
        return;
      }
      resolve(values);
    },
  });

  return (
    <Card.Root className="max-w-xl mx-auto border-border-subtle/60 bg-surface-0/60 shadow-2xl transition-all duration-300">
      <form.Form className="block">
        <Card.Body padding="lg" className="gap-1.5 space-y-2">
          <Card.Title className="line-clamp-none group-hover:text-transparent text-2xl font-bold tracking-tight bg-gradient-to-r from-surface-1 to-surface-3 bg-clip-text text-transparent">
            {schema.title}
          </Card.Title>
          <Card.Description className="line-clamp-none text-sm text-fg-secondary">
            {schema.description}
          </Card.Description>
        </Card.Body>

        <Card.Body padding="lg" className="block pt-0 space-y-6">
          {errorMsg && (
            <div className="flex items-start space-x-2 rounded-xl border border-danger/20 bg-danger/5 p-3.5 text-sm text-danger animate-in fade-in slide-in-from-top-1">
              <Icon name="error" size={20} className="flex-shrink-0 mt-0.5" />
              <span>{errorMsg}</span>
            </div>
          )}

          <div className="space-y-4">
            {schema.fields.map((field) => (
              <form.Field key={field.name} name={`values.${field.name}`}>
                {(control) => (
                  <InterceptionFormField
                    field={field}
                    id={control.id}
                    value={String(control.value ?? "")}
                    onChange={control.setValue}
                    disabled={isPending}
                    locale={locale}
                  />
                )}
              </form.Field>
            ))}
          </div>
        </Card.Body>

        <Card.Footer className="px-6 pb-6 text-sm">
          <Button
            type="submit"
            disabled={isPending}
            className="w-full flex items-center justify-center space-x-2 bg-gradient-to-r from-success to-success hover:from-success hover:to-success text-on-accent font-medium shadow-lg hover:shadow-success/10 transition-all duration-300"
          >
            {isPending ? (
              <>
                <Icon name="loader" size={16} className="animate-spin" />
                <span>{t.interception.resolving}</span>
              </>
            ) : (
              <span>{t.interception.submitPreferences}</span>
            )}
          </Button>
        </Card.Footer>
      </form.Form>
    </Card.Root>
  );
}
