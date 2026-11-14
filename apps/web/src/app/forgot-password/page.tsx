"use client";

import * as React from "react";
import Link from "next/link";
import { Alert } from "@krizaka/ui/alert";
import { createKrizakaForm } from "@krizaka/ui/form";
import { z } from "zod";
import { useAuth } from "@/core/hooks/useAuth";
import { useTranslation } from "@/core/context/LocaleContext";
import { useRouter } from "next/navigation";
import { InputField } from "@/features/auth/components/InputField";
import { AuthScreen } from "@/features/auth/components/AuthScreen";
import { AuthGate } from "@/features/auth/components/AuthGate";

/** The address a reset link is sent to. */
const forgotForm = createKrizakaForm({
  schema: z.object({ email: z.string() }),
  defaultValues: { email: "" },
});

/** The password reset screen, on the entry template. Calls POST /api/v1/auth/forgot. */
function ForgotPasswordPageContent() {
  const router = useRouter();
  const { isAuthenticated } = useAuth();
  const { t } = useTranslation();

  const [error, setError] = React.useState("");
  const [success, setSuccess] = React.useState(false);
  const form = forgotForm.useForm({
    onSubmit: async ({ email }) => {
      if (!email.trim()) {
        return;
      }
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
      }
    },
  });

  React.useEffect(() => {
    if (isAuthenticated) router.push("/");
  }, [isAuthenticated, router]);

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
        <form.Form className="flex flex-col gap-4">
          <InputField
            name="email"
            id="forgot-email"
            label={t.auth.emailLabel}
            type="email"
            required
            placeholder="name@example.com"
            autoComplete="email"
          />
          <form.Watch name="email">
            {(email) => (
              <form.Submit
                id="btn-forgot-submit"
                variant="primary"
                size="lg"
                disabled={!String(email).trim()}
                className="w-full"
              >
                {t.auth.forgotSubmit}
              </form.Submit>
            )}
          </form.Watch>
        </form.Form>
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
