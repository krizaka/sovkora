"use client";

import * as React from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { signIn } from "next-auth/react";
import { CodeNameLine, OrazakaLogo } from "@krizaka/ui";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { AuthLayout } from "@krizaka/ui/auth-layout";
import { Field, Input } from "@krizaka/ui/field";
import { consoleCodeName } from "@/console/core/config/code-name";
import { consoleCopy } from "@/console/core/copy/console";

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
  const codeName = consoleCodeName();
  const router = useRouter();
  const params = useSearchParams();
  const [pending, setPending] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const signInWithCredentials = async (event: React.SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (preview) return;
    const form = new FormData(event.currentTarget);
    setPending(true);
    setError(null);
    try {
      const result = await signIn("credentials", {
        email: String(form.get("email") ?? ""),
        password: String(form.get("password") ?? ""),
        redirect: false,
      });
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
  };

  const onSubmit: React.SubmitEventHandler<HTMLFormElement> = (event) => {
    void signInWithCredentials(event);
  };

  return (
    <div className="brand-orazaka">
      <AuthLayout
        brand={
          <>
            <OrazakaLogo size={28} animated={false} /> {consoleCopy.product} {consoleCopy.console}
            {codeName ? <CodeNameLine className="ml-2">{codeName}</CodeNameLine> : null}
          </>
        }
        promise={copy.title}
        aside={
          <div className="flex flex-col gap-6">
            <OrazakaLogo size={112} />
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
        <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
          <Field.Root>
            <Field.Label htmlFor="login-email">{copy.email}</Field.Label>
            <Input
              id="login-email"
              name="email"
              type="email"
              autoComplete="username"
              required
              disabled={pending || Boolean(preview)}
              invalid={Boolean(error)}
            />
          </Field.Root>
          <Field.Root>
            <Field.Label htmlFor="login-password">{copy.password}</Field.Label>
            <Input
              id="login-password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              disabled={pending || Boolean(preview)}
              invalid={Boolean(error)}
            />
          </Field.Root>
          <Button
            type="submit"
            variant="primary"
            size="lg"
            loading={pending}
            disabled={Boolean(preview)}
            className="w-full"
          >
            {copy.submit}
          </Button>
        </form>
      </AuthLayout>
    </div>
  );
}
