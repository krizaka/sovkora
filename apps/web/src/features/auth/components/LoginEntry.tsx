"use client";

import * as React from "react";
import { signIn } from "next-auth/react";
import { useRouter } from "next/navigation";
import { Alert } from "@krizaka/ui/alert";
import { useAuthFlags } from "@/core/context/AuthFlagsContext";
import { useTranslation } from "@/core/context/LocaleContext";
import { useAuth } from "@/core/hooks/useAuth";
import { LoginCard } from "@/features/auth/components/LoginForm";

/** The connected sign-in: next-auth credentials and OAuth. Mounted only while `AUTH_LOGIN_ENABLED` is on. */
export function LoginForm() {
  const router = useRouter();
  const { isAuthenticated, loginWithGithub, loginWithGoogle } = useAuth();
  const { t } = useTranslation();
  const [error, setError] = React.useState("");
  const [isLoading, setIsLoading] = React.useState(false);

  React.useEffect(() => {
    if (isAuthenticated) router.push("/");
  }, [isAuthenticated, router]);

  const fail = (message: string) => setError(message);

  const handleLogin = async (email: string, password: string) => {
    setIsLoading(true);
    setError("");
    try {
      const res = await signIn("credentials", { email, password, redirect: false });
      if (res?.error) fail(t.auth.invalidCredentials);
      else if (res?.ok) router.push("/");
    } catch {
      fail(t.auth.unexpectedError);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <LoginCard
      error={error}
      isLoading={isLoading}
      onSubmit={handleLogin}
      onGithub={loginWithGithub}
      onGoogle={loginWithGoogle}
    />
  );
}

/**
 * Sign-in while it is closed (`AUTH_LOGIN_ENABLED` off): the real screen of the app, disabled, under a calm banner that
 * says when it opens. Nothing here touches the session or the backend.
 */
export function LoginPreview() {
  const { t } = useTranslation();
  const c = t.landing.closed;
  return (
    <LoginCard
      preview
      notice={
        <Alert tone="info" role="status">
          {c.signInBanner}
        </Alert>
      }
    />
  );
}

/** The open sign-in when `AUTH_LOGIN_ENABLED` is on, the disabled preview of the same screen otherwise. */
export function LoginEntry() {
  const flags = useAuthFlags();
  return flags.login ? <LoginForm /> : <LoginPreview />;
}
