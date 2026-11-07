"use client";

import * as React from "react";
import Link from "next/link";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { Field, Input } from "@krizaka/ui/field";
import { useAuthFlags } from "@/core/context/AuthFlagsContext";
import { useTranslation } from "@/core/context/LocaleContext";
import { AuthScreen } from "@/features/auth/components/AuthScreen";

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
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const providers = preview ? { github: false, google: false } : { github: flags.github, google: flags.google };

  const submit = (e: React.SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!preview) onSubmit?.(email, password);
  };

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
      <form id="form-login" onSubmit={submit} className="flex flex-col gap-4">
        {notice}
        {error && <Alert tone="danger">{error}</Alert>}
        <Field.Root>
          <Field.Label htmlFor="login-email">{t.auth.emailLabel}</Field.Label>
          <Input
            id="login-email"
            type="email"
            placeholder="name@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            disabled={preview}
            autoComplete="email"
            invalid={Boolean(error)}
          />
        </Field.Root>
        <Field.Root>
          <Field.Label htmlFor="login-password">{t.auth.passwordLabel}</Field.Label>
          <Input
            id="login-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            disabled={preview}
            autoComplete="current-password"
            invalid={Boolean(error)}
          />
          <Field.Hint>
            <Link href="/forgot-password" className="text-fg-accent hover:underline">
              {t.auth.forgotPasswordLink}
            </Link>
          </Field.Hint>
        </Field.Root>
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
      </form>

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
