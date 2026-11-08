import * as React from "react";
import { Button } from "@krizaka/ui/button";
import { Field, Input, Select } from "@krizaka/ui/field";
import { useRegister } from "@/features/auth/hooks/useRegister";
import { useTranslation } from "@/core/context/LocaleContext";

// ─── Supported UI languages ────────────────────────────────────────────────
const SUPPORTED_LANGUAGES = [
  { code: "en", label: "English" },
  { code: "fr", label: "Français" },
] as const;

/**
 * Properties required by the {@link RegisterForm} component.
 */
interface RegisterFormProps {
  /** Callback triggered when registration succeeds. */
  onSuccess: (msg: string) => void;
  /** Callback triggered when registration fails. */
  onError: (msg: string) => void;
}

/**
 * RegisterForm component renders the user registration credentials inputs,
 * validates values on the client side, and submits to the Next.js BFF endpoint.
 */
export function RegisterForm({ onSuccess, onError }: Readonly<RegisterFormProps>) {
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [username, setUsername] = React.useState("");
  const [language, setLanguage] = React.useState("en");
  const [confirmPassword, setConfirmPassword] = React.useState("");

  const { t } = useTranslation();
  const { register, isPending } = useRegister({ onSuccess, onError });

  const handleRegister = (e: React.SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    onError("");
    onSuccess("");

    // Client-side guards
    if (username.trim().length < 3) {
      onError(t.auth.usernameMinLength);
      return;
    }
    if (password.length < 8) {
      onError(t.auth.passwordMinLength);
      return;
    }
    if (password !== confirmPassword) {
      onError(t.auth.passwordsDoNotMatch);
      return;
    }

    register({
      username: username.trim(),
      email,
      password,
      language,
    });
  };

  const field = (id: string, label: string, input: React.ReactNode) => (
    <Field.Root>
      <Field.Label htmlFor={id}>{label}</Field.Label>
      {input}
    </Field.Root>
  );

  return (
    <form id="form-register" onSubmit={handleRegister} className="flex flex-col gap-4">
      {field(
        "reg-username",
        t.auth.usernameLabel,
        <Input
          id="reg-username"
          type="text"
          placeholder="your_name"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
          minLength={3}
          autoComplete="username"
        />,
      )}
      {field(
        "reg-email",
        t.auth.emailLabel,
        <Input
          id="reg-email"
          type="email"
          placeholder="name@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          autoComplete="email"
        />,
      )}
      {field(
        "reg-password",
        t.auth.passwordLabel,
        <Input
          id="reg-password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={8}
          autoComplete="new-password"
        />,
      )}
      {field(
        "reg-confirm-password",
        t.auth.confirmPasswordLabel,
        <Input
          id="reg-confirm-password"
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          required
          autoComplete="new-password"
        />,
      )}
      {field(
        "reg-language",
        t.auth.preferredLanguageLabel,
        <Select id="reg-language" value={language} onChange={(e) => setLanguage(e.target.value)}>
          {SUPPORTED_LANGUAGES.map((lang) => (
            <option key={lang.code} value={lang.code}>
              {lang.label}
            </option>
          ))}
        </Select>,
      )}
      <Button id="btn-register-submit" type="submit" variant="primary" size="lg" loading={isPending} className="w-full">
        {isPending ? t.auth.creatingAccountBtn : t.auth.createAccountBtn}
      </Button>
      <p className="text-center text-xs text-fg-secondary">{t.auth.termsNotice}</p>
    </form>
  );
}
