import React, { useState } from "react";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { Button, TextField, Txt } from "@krizaka/ui/native";
import type { RootStackParamList } from "../navigation/AppNavigator";
import { useAuth } from "../core/AuthContext";
import { errorText } from "../i18n/errorText";
import { useI18n } from "../i18n";
import { AuthShell } from "../ui/AuthShell";

type Props = NativeStackScreenProps<RootStackParamList, "Register">;

export function RegisterScreen({ navigation }: Props): React.JSX.Element {
  const { t, locale } = useI18n();
  const { register } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const canSubmit = username.trim() !== "" && email.trim() !== "" && password !== "" && !isSubmitting;

  const submit = async () => {
    if (!canSubmit) return;
    setIsSubmitting(true);
    setError(null);
    try {
      const outcome = await register(username.trim(), email.trim(), password, locale);
      if (outcome.kind === "verification-required") {
        // No session was issued, so the navigator will not switch stacks. Saying so beats
        // leaving the actor on a form that looks like it did nothing.
        setNotice(t("auth.register.verify", { email: outcome.email }));
      }
    } catch (cause: unknown) {
      setError(errorText(cause, t, "auth.register.failed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthShell title={t("auth.register.title")} subtitle={t("auth.register.subtitle")}>
      <TextField
        testID="register-username"
        label={t("auth.username")}
        autoCapitalize="none"
        disabled={isSubmitting}
        value={username}
        onValueChange={setUsername}
      />
      <TextField
        testID="register-email"
        label={t("auth.email")}
        keyboardType="email-address"
        autoCapitalize="none"
        disabled={isSubmitting}
        value={email}
        onValueChange={setEmail}
      />
      <TextField
        testID="register-password"
        label={t("auth.password")}
        secureTextEntry
        disabled={isSubmitting}
        value={password}
        onValueChange={setPassword}
      />
      {notice !== null && (
        <Txt tone="secondary" variant="caption">
          {notice}
        </Txt>
      )}
      {error !== null && (
        <Txt tone="danger" variant="caption">
          {error}
        </Txt>
      )}
      <Button
        testID="register-submit"
        variant="primary"
        label={t("auth.register.submit")}
        size="lg"
        loading={isSubmitting}
        disabled={!canSubmit}
        onPress={() => void submit()}
      />
      <Button variant="ghost" label={t("auth.register.haveAccount")} onPress={() => navigation.navigate("Login")} />
    </AuthShell>
  );
}
