"use client";

import * as React from "react";
import Link from "next/link";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { createKrizakaForm } from "@krizaka/ui/form";
import { z } from "zod";
import { useAuthFlags } from "@/core/context/AuthFlagsContext";
import { useTranslation } from "@/core/context/LocaleContext";
import { InputField } from "@/features/auth/components/InputField";
import { AuthScreen } from "@/features/auth/components/AuthScreen";

/** The credentials: each is required, said in the person's language through the screen's own words. */
const signInCredentials = createKrizakaForm({
  schema: z.object({ email: z.string().min(1, "auth.email"), password: z.string().min(1, "auth.password") }),
  defaultValues: { email: "", password: "" },
});

interface LoginCardProps {
  /** Closed site: the real screen, every field and button disabled, nothing wired to the backend. */
  preview?: boolean;
  /** A calm banner above the form (the preview's). */
  notice?: React.ReactNode;
  error?: string;
  isLoading?: boolean;
  onSubmit?: (email: string, password: string) => void;
  onGithub?: () => void;
  onGoogle?: () => void;
}

/**
 * The sign-in screen itself, on the entry template (`AuthScreen`). Pure view: the connected `LoginForm` wires it to
 * next-auth, `LoginPreview` shows it disabled while sign-in is closed. The "Register" way appears only while sign-up
 * is open, and each OAuth button only when its provider is offered (`AuthFlags.github` / `google`).
 */
export function LoginCard({
  preview = false,
  notice,
  error = "",
  isLoading = false,
  onSubmit,
  onGithub,
  onGoogle,
}: Readonly<LoginCardProps>) {
  const { t } = useTranslation();
  const flags = useAuthFlags();
  const words: Record<string, string> = {
    "auth.email": t.interception.fieldRequired(t.auth.emailLabel),
    "auth.password": t.interception.fieldRequired(t.auth.passwordLabel),
  };
  const form = signInCredentials.useForm({
    i18n: { t: (key) => words[key] },
    onSubmit: ({ email, password }) => {
      if (!preview) {
        onSubmit?.(email, password);
      }
    },
  });
  const providers = preview ? { github: false, google: false } : { github: flags.github, google: flags.google };

  return (
    <AuthScreen
      title={t.auth.loginTitle}
      description={t.auth.loginSubtitle}
      footer={
        flags.signup ? (
          <>
            {t.auth.noAccount}{" "}
            <Link href="/register" className="font-semibold text-fg-accent hover:underline">
              {t.auth.registerLink}
            </Link>
          </>
        ) : undefined
      }
    >
      <form.Form id="form-login">
        {notice}
        {error && <Alert tone="danger">{error}</Alert>}
        <InputField
          name="email"
          id="login-email"
          label={t.auth.emailLabel}
          type="email"
          placeholder="name@example.com"
          required
          disabled={preview}
          autoComplete="email"
          invalid={Boolean(error)}
        />
        <InputField
          name="password"
          id="login-password"
          label={t.auth.passwordLabel}
          type="password"
          required
          disabled={preview}
          autoComplete="current-password"
          invalid={Boolean(error)}
          hint={
            <Link href="/forgot-password" className="text-fg-accent hover:underline">
              {t.auth.forgotPasswordLink}
            </Link>
          }
        />
        <Button
          id="btn-login-submit"
          type="submit"
          variant="primary"
          size="lg"
          loading={isLoading && !preview}
          disabled={preview}
          className="w-full"
        >
          {isLoading ? t.auth.signingInBtn : t.auth.signInBtn}
        </Button>
      </form.Form>

      {(providers.github || providers.google) && (
        <nav className="flex flex-col gap-3" aria-label={t.a11y.socialLogin}>
          <p className="text-center text-xs text-fg-secondary">{t.auth.orContinueWith}</p>
          <div className="grid gap-3 sm:grid-cols-2">
            {providers.github && (
              <Button id="btn-github" type="button" variant="secondary" size="lg" onClick={onGithub}>
                GitHub
              </Button>
            )}
            {providers.google && (
              <Button id="btn-google" type="button" variant="secondary" size="lg" onClick={onGoogle}>
                Google
              </Button>
            )}
          </div>
        </nav>
      )}
    </AuthScreen>
  );
}
