"use client";

import * as React from "react";
import Link from "next/link";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { Field, Input } from "@krizaka/ui/field";
import { useAuth } from "@/core/hooks/useAuth";
import { useTranslation } from "@/core/context/LocaleContext";
import { useRouter } from "next/navigation";
import { AuthScreen } from "@/features/auth/components/AuthScreen";
import { AuthGate } from "@/features/auth/components/AuthGate";

/** The password reset screen, on the entry template. Calls POST /api/v1/auth/forgot. */
function ForgotPasswordPageContent() {
  const router = useRouter();
  const { isAuthenticated } = useAuth();
  const { t } = useTranslation();

  const [email, setEmail] = React.useState("");
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [error, setError] = React.useState("");
  const [success, setSuccess] = React.useState(false);

  React.useEffect(() => {
    if (isAuthenticated) router.push("/");
  }, [isAuthenticated, router]);

  const handleSubmit = async (e: React.SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!email.trim()) return;
    setIsSubmitting(true);
    setError("");
    setSuccess(false);

    try {
      const res = await fetch("/api/v1/auth/forgot", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });
      if (res.ok) {
        setSuccess(true);
      } else {
        const data = await res.text();
        setError(data || t.auth.forgotError || "Failed to send reset email.");
      }
    } catch {
      setError(t.auth.forgotError || "An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthScreen
      title={t.auth.forgotTitle}
      description={t.auth.forgotSubtitle}
      footer={
        <Link href="/login" className="font-semibold text-fg-accent hover:underline">
          {t.auth.backToLogin}
        </Link>
      }
    >
      {error && <Alert tone="danger">{error}</Alert>}
      {success ? (
        <Alert tone="success" role="status" title={t.auth.forgotSuccess}>
          {t.auth.forgotSuccessDetail}
        </Alert>
      ) : (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <Field.Root>
            <Field.Label htmlFor="forgot-email">{t.auth.emailLabel}</Field.Label>
            <Input
              id="forgot-email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@example.com"
              autoComplete="email"
            />
          </Field.Root>
          <Button
            id="btn-forgot-submit"
            type="submit"
            variant="primary"
            size="lg"
            loading={isSubmitting}
            disabled={!email.trim()}
            className="w-full"
          >
            {t.auth.forgotSubmit}
          </Button>
        </form>
      )}
    </AuthScreen>
  );
}

/** The page is closed behind AUTH_LOGIN_ENABLED (default false): a calm notice, nothing mounts, no backend call. */
export default function ForgotPasswordPage() {
  return (
    <AuthGate flag="login">
      <ForgotPasswordPageContent />
    </AuthGate>
  );
}
