"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { useAuth } from "@/core/hooks/useAuth";
import { useAuthFlags } from "@/core/context/AuthFlagsContext";
import { RegisterForm } from "@/features/auth/components/RegisterForm";
import { useTranslation } from "@/core/context/LocaleContext";
import { AuthScreen } from "@/features/auth/components/AuthScreen";
import { AuthGate } from "@/features/auth/components/AuthGate";

/** The sign-up screen, on the entry template. OAuth buttons only for a provider that is offered. */
function RegisterPageContent() {
  const router = useRouter();
  const { isAuthenticated, loginWithGithub, loginWithGoogle } = useAuth();
  const flags = useAuthFlags();
  const { t } = useTranslation();

  const [error, setError] = React.useState("");
  const [success, setSuccess] = React.useState("");

  React.useEffect(() => {
    if (isAuthenticated) router.push("/");
  }, [isAuthenticated, router]);

  return (
    <AuthScreen
      title={t.auth.registerTitle}
      description={t.auth.registerSubtitle}
      footer={
        <>
          {t.auth.alreadyHaveAccount}{" "}
          <Link href="/login" className="font-semibold text-fg-accent hover:underline">
            {t.auth.signInBtn}
          </Link>
        </>
      }
    >
      {error && <Alert tone="danger">{error}</Alert>}
      {success && (
        <Alert tone="success" role="status">
          {success}
        </Alert>
      )}
      <RegisterForm onSuccess={setSuccess} onError={setError} />
      {(flags.github || flags.google) && (
        <nav className="flex flex-col gap-3" aria-label={t.a11y.socialRegister}>
          <p className="text-center text-xs text-fg-secondary">{t.auth.orContinueWith}</p>
          <div className="grid gap-3 sm:grid-cols-2">
            {flags.github && (
              <Button id="btn-github" type="button" variant="secondary" size="lg" onClick={loginWithGithub}>
                GitHub
              </Button>
            )}
            {flags.google && (
              <Button id="btn-google" type="button" variant="secondary" size="lg" onClick={loginWithGoogle}>
                Google
              </Button>
            )}
          </div>
        </nav>
      )}
    </AuthScreen>
  );
}

/** The page is closed behind AUTH_SIGNUP_ENABLED (default false): a calm notice, nothing mounts, no backend call. */
export default function RegisterPage() {
  return (
    <AuthGate flag="signup">
      <RegisterPageContent />
    </AuthGate>
  );
}
