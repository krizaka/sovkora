"use client";

import * as React from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { signIn } from "next-auth/react";
import { SovkoraLogo } from "@krizaka/ui";
import { Alert } from "@krizaka/ui/alert";
import { AuthLayout } from "@krizaka/ui/auth-layout";
import { createKrizakaForm } from "@krizaka/ui/form";
import { z } from "zod";
import { consoleCopy } from "@/console/core/copy/console";

/** The operator's credentials: what the identity service checks, so nothing is refused before it answers. */
const signInForm = createKrizakaForm({
  schema: z.object({ email: z.string(), password: z.string() }),
  defaultValues: { email: "", password: "" },
});

/** The closed site's variant: the same screen disabled, under a calm banner that points at the public demo. */
export interface ClosedPreview {
  banner: string;
  link: string;
  href: string;
  locale: string;
}

/** Only a path of this console: never an absolute URL a crafted link could send the operator to. */
function safeCallback(value: string | null): string {
  return value && value.startsWith("/") && !value.startsWith("//") ? value : "/";
}

/**
 * The operator's sign-in: email and password, checked by the identity service through next-auth's credentials
 * provider, which also refuses an account without the administrator role. Inputs lock while the request runs.
 */
export function LoginForm({ preview }: Readonly<{ preview?: ClosedPreview }>) {
  const copy = consoleCopy.login;
  const router = useRouter();
  const params = useSearchParams();
  const [pending, setPending] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const form = signInForm.useForm({
    onSubmit: async (values) => {
      if (preview) {
        return;
      }
      setPending(true);
      setError(null);
      try {
        const result = await signIn("credentials", { email: values.email, password: values.password, redirect: false });
        if (result?.ok) {
          router.replace(safeCallback(params.get("callbackUrl")));
          router.refresh();
          return;
        }
        setError(copy.refused);
      } catch {
        setError(copy.unreachable);
      }
      setPending(false);
    },
  });
  const locked = pending || Boolean(preview);

  return (
    <div className="brand-orazaka">
      <AuthLayout
        brand={
          <>
            <SovkoraLogo size={28} animated={false} /> {consoleCopy.product} {consoleCopy.console}
          </>
        }
        promise={copy.title}
        aside={
          <div className="flex flex-col gap-6">
            <SovkoraLogo size={112} />
            <p>{copy.lead}</p>
          </div>
        }
        asideFooter={
          <>
            © {new Date().getFullYear()} Sovkora · {consoleCopy.product} {consoleCopy.console}
          </>
        }
        title={copy.title}
        description={copy.lead}
      >
        {preview && (
          <Alert tone="info" role="status">
            <span lang={preview.locale}>{preview.banner}</span>{" "}
            <a href={preview.href} className="font-medium underline underline-offset-4">
              {preview.link}
            </a>
          </Alert>
        )}
        {error && <Alert tone="danger">{error}</Alert>}
        <form.Form className="flex flex-col gap-4">
          <form.TextField name="email" label={copy.email} type="email" autoComplete="username" disabled={locked} />
          <form.TextField
            name="password"
            label={copy.password}
            type="password"
            autoComplete="current-password"
            disabled={locked}
          />
          <form.Submit variant="primary" size="lg" disabled={Boolean(preview)} className="w-full">
            {copy.submit}
          </form.Submit>
        </form.Form>
      </AuthLayout>
    </div>
  );
}
