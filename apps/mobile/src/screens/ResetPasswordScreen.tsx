import React, { useState } from "react";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { Button, TextField, Txt } from "@krizaka/ui/native";
import type { RootStackParamList } from "../navigation/AppNavigator";
import { apiRequest } from "../config/api";
import { errorText } from "../i18n/errorText";
import { useT } from "../i18n";
import { AuthShell } from "../ui/AuthShell";

type Props = NativeStackScreenProps<RootStackParamList, "ResetPassword">;

export function ResetPasswordScreen({ route, navigation }: Props): React.JSX.Element {
  const { token } = route.params;
  const t = useT();
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mismatch = confirmPassword !== "" && newPassword !== confirmPassword;
  const canSubmit = newPassword !== "" && !mismatch && !isSubmitting;

  const submit = async () => {
    if (!canSubmit) return;
    setIsSubmitting(true);
    setError(null);
    try {
      await apiRequest("/api/v1/auth/reset", { method: "POST", body: { token, newPassword } });
      navigation.navigate("Login");
    } catch (cause: unknown) {
      setError(errorText(cause, t, "auth.reset.failed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthShell title={t("auth.reset.title")} subtitle={t("auth.reset.subtitle")}>
      <TextField
        testID="reset-new-password"
        label={t("auth.reset.newPassword")}
        secureTextEntry
        value={newPassword}
        onValueChange={setNewPassword}
      />
      <TextField
        testID="reset-confirm-password"
        label={t("auth.reset.confirm")}
        secureTextEntry
        value={confirmPassword}
        onValueChange={setConfirmPassword}
        error={mismatch ? t("auth.reset.mismatch") : undefined}
      />
      {error !== null && (
        <Txt tone="danger" variant="caption">
          {error}
        </Txt>
      )}
      <Button
        testID="reset-submit"
        variant="primary"
        label={t("auth.reset.submit")}
        size="lg"
        loading={isSubmitting}
        disabled={!canSubmit}
        onPress={() => void submit()}
      />
      <Button variant="ghost" label={t("auth.forgotScreen.back")} onPress={() => navigation.navigate("Login")} />
    </AuthShell>
  );
}
