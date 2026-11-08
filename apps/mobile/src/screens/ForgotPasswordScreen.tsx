import React, { useState } from "react";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { Button, TextField, Txt } from "@krizaka/ui/native";
import type { RootStackParamList } from "../navigation/AppNavigator";
import { apiRequest } from "../config/api";
import { errorText } from "../i18n/errorText";
import { useT } from "../i18n";
import { AuthShell } from "../ui/AuthShell";

type Props = NativeStackScreenProps<RootStackParamList, "ForgotPassword">;

export function ForgotPasswordScreen({ navigation }: Props): React.JSX.Element {
  const t = useT();
  const [email, setEmail] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  const canSubmit = email.trim() !== "" && !isSubmitting;

  const submit = async () => {
    if (!canSubmit) return;
    setIsSubmitting(true);
    setError(null);
    try {
      await apiRequest("/api/v1/auth/forgot", { method: "POST", body: { email: email.trim() } });
      // Deliberately the same message whether or not the address is known: the service answers
      // identically too, because a "no such account" reply is an account-enumeration oracle.
      setSent(true);
    } catch (cause: unknown) {
      setError(errorText(cause, t, "auth.forgotScreen.failed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthShell title={t("auth.forgotScreen.title")} subtitle={t("auth.forgotScreen.subtitle")}>
      <TextField
        testID="forgot-email"
        label={t("auth.emailAddress")}
        keyboardType="email-address"
        autoCapitalize="none"
        value={email}
        onValueChange={setEmail}
      />
      {sent && (
        <Txt tone="secondary" variant="caption">
          {t("auth.forgotScreen.sent")}
        </Txt>
      )}
      {error !== null && (
        <Txt tone="danger" variant="caption">
          {error}
        </Txt>
      )}
      <Button
        testID="forgot-submit"
        variant="primary"
        label={t("auth.forgotScreen.submit")}
        size="lg"
        loading={isSubmitting}
        disabled={!canSubmit}
        onPress={() => void submit()}
      />
      <Button variant="ghost" label={t("auth.forgotScreen.back")} onPress={() => navigation.navigate("Login")} />
    </AuthShell>
  );
}
