"use client";

/**
 * @file verify/page.tsx
 * @description Account activation and verification page.
 * Processes the verification token via the BFF GraphQL proxy and renders corresponding status screens.
 */

import * as React from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { Suspense } from "react";
import { Button } from "@krizaka/ui/button";
import { createKrizakaForm } from "@krizaka/ui/form";
import { z } from "zod";
import { InputField } from "@/features/auth/components/InputField";
import { AuthScreen } from "@/features/auth/components/AuthScreen";
import { useTranslation } from "@/core/context/LocaleContext";
import { useVerifyEmail } from "@/features/auth/hooks/useVerifyEmail";
import { AuthGate } from "@/features/auth/components/AuthGate";

/** The activation token, typed when the link did not carry it. */
const verifyForm = createKrizakaForm({
  schema: z.object({ token: z.string() }),
  defaultValues: { token: "" },
});

function VerifyPageContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { t } = useTranslation();
  const tokenParam = searchParams.get("token");

  const { verify, status, errorMsg } = useVerifyEmail();
  const form = verifyForm.useForm({
    defaultValues: { token: tokenParam ?? "" },
    onSubmit: ({ token }) => {
      if (token.trim()) {
        verify(token);
      }
    },
  });

  // If token parameter exists in URL, trigger verification automatically
  React.useEffect(() => {
    if (tokenParam) {
      verify(tokenParam);
    }
  }, [tokenParam, verify]);

  const heading = {
    loading: { title: t.verify.loadingMessage, description: undefined },
    success: { title: t.verify.successTitle, description: t.verify.successDescription },
    error: { title: t.verify.errorTitle, description: errorMsg },
    idle: { title: t.verify.idleTitle, description: t.verify.idleDescription },
  }[status];

  return (
    <AuthScreen title={heading.title} description={heading.description}>
      {status === "loading" && (
        <Button type="button" variant="secondary" size="lg" loading disabled className="w-full">
          {t.verify.loadingMessage}
        </Button>
      )}

      {status === "success" && (
        <Button type="button" variant="primary" size="lg" onClick={() => router.push("/login")} className="w-full">
          {t.verify.goToLogin}
        </Button>
      )}

      {(status === "error" || status === "idle") && (
        <form.Form className="flex flex-col gap-4">
          <InputField
            name="token"
            id="verify-token"
            label={status === "error" ? t.verify.enterTokenManually : t.verify.tokenLabel}
            type="text"
            placeholder={t.verify.tokenPlaceholder}
            autoComplete="one-time-code"
            required
            invalid={status === "error"}
          />
          <form.Submit variant="primary" size="lg" className="w-full">
            {status === "error" ? t.verify.retryVerification : t.verify.activateAccount}
          </form.Submit>
        </form.Form>
      )}
    </AuthScreen>
  );
}

/**
 * VerifyPage component that wraps VerifyPageContent in a Suspense boundary.
 *
 * @returns The Suspense wrapped account verification page.
 */
function VerifyPageSuspended() {
  return (
    <Suspense fallback={null}>
      <VerifyPageContent />
    </Suspense>
  );
}

/** The page is closed behind AUTH_LOGIN_ENABLED (default false): a calm notice, nothing mounts, no backend call. */
export default function VerifyPage() {
  return (
    <AuthGate flag="login">
      <VerifyPageSuspended />
    </AuthGate>
  );
}
