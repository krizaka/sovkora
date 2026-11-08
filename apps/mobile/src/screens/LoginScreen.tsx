import React, { useState } from "react";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { Button, TextField, Txt } from "@krizaka/ui/native";
import type { RootStackParamList } from "../navigation/AppNavigator";
import { ApiError } from "../config/api";
import { useAuth } from "../core/AuthContext";
import { errorText } from "../i18n/errorText";
import { useT } from "../i18n";
import { useSignupOpen } from "../config/signupOpen";
import { AuthShell } from "../ui/AuthShell";

type Props = NativeStackScreenProps<RootStackParamList, "Login">;

export function LoginScreen({ navigation }: Props): React.JSX.Element {
  const t = useT();
  const { signIn } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const signupOpen = useSignupOpen();

  const canSubmit = email.trim() !== "" && password !== "" && !isSubmitting;

  const submit = async () => {
    if (!canSubmit) return;
    setIsSubmitting(true);
    setError(null);
    try {
      await signIn(email.trim(), password);
      // No navigate(): the navigator swaps the auth stack for the app stack as soon as the
      // session exists, so there is exactly one place that decides where a signed-in user lands.
    } catch (cause: unknown) {
      // A 401 here is a wrong e-mail or password, not an expired session.
      setError(
        cause instanceof ApiError && cause.status === 401
          ? t("auth.invalidCredentials")
          : errorText(cause, t, "auth.signInFailed"),
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthShell title={t("auth.name")} subtitle={t("auth.tagline")}>
      <TextField
        testID="login-email"
        label={t("auth.email")}
        keyboardType="email-address"
        autoCapitalize="none"
        autoComplete="email"
        disabled={isSubmitting}
        value={email}
        onValueChange={setEmail}
      />
      <TextField
        testID="login-password"
        label={t("auth.password")}
        secureTextEntry
        autoComplete="current-password"
        disabled={isSubmitting}
        value={password}
        onValueChange={setPassword}
        onSubmitEditing={() => void submit()}
      />
      {error !== null && (
        <Txt tone="danger" variant="caption">
          {error}
        </Txt>
      )}
      <Button
        testID="login-submit"
        variant="primary"
        label={t("auth.signIn")}
        size="lg"
        loading={isSubmitting}
        disabled={!canSubmit}
        onPress={() => void submit()}
      />
      <Button
        variant="ghost"
        label={t("auth.forgot")}
        disabled={isSubmitting}
        onPress={() => navigation.navigate("ForgotPassword")}
      />
      {signupOpen && (
        <Button
          testID="login-create-account"
          variant="ghost"
          label={t("auth.create")}
          disabled={isSubmitting}
          onPress={() => navigation.navigate("Register")}
        />
      )}
    </AuthShell>
  );
}
