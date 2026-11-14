import { Button } from "@krizaka/ui/button";
import { createKrizakaForm } from "@krizaka/ui/form";
import { z } from "zod";
import { InputField } from "@/features/auth/components/InputField";
import { useRegister } from "@/features/auth/hooks/useRegister";
import { useTranslation } from "@/core/context/LocaleContext";

// ─── Supported UI languages ────────────────────────────────────────────────
const SUPPORTED_LANGUAGES = [
  { code: "en", label: "English" },
  { code: "fr", label: "Français" },
] as const;

/** What the person types; the length and match guards speak through the parent's banner, the e-mail through its field. */
const registration = createKrizakaForm({
  schema: z.object({
    username: z.string(),
    email: z.string().min(1, "auth.email"),
    password: z.string(),
    confirmPassword: z.string(),
    language: z.string(),
  }),
  defaultValues: { username: "", email: "", password: "", confirmPassword: "", language: "en" },
});

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
  const { t } = useTranslation();
  const { register, isPending } = useRegister({ onSuccess, onError });
  const words: Record<string, string> = { "auth.email": t.interception.fieldRequired(t.auth.emailLabel) };

  const form = registration.useForm({
    i18n: { t: (key) => words[key] },
    onSubmit: ({ username, email, password, confirmPassword, language }) => {
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

      register({ username: username.trim(), email, password, language });
    },
  });

  return (
    <form.Form id="form-register">
      <InputField
        name="username"
        id="reg-username"
        label={t.auth.usernameLabel}
        type="text"
        placeholder="your_name"
        required
        minLength={3}
        autoComplete="username"
      />
      <InputField
        name="email"
        id="reg-email"
        label={t.auth.emailLabel}
        type="email"
        placeholder="name@example.com"
        required
        autoComplete="email"
      />
      <InputField
        name="password"
        id="reg-password"
        label={t.auth.passwordLabel}
        type="password"
        required
        minLength={8}
        autoComplete="new-password"
      />
      <InputField
        name="confirmPassword"
        id="reg-confirm-password"
        label={t.auth.confirmPasswordLabel}
        type="password"
        required
        autoComplete="new-password"
      />
      <form.Select
        name="language"
        label={t.auth.preferredLanguageLabel}
        options={SUPPORTED_LANGUAGES.map((lang) => ({ value: lang.code, label: lang.label }))}
      />
      <Button id="btn-register-submit" type="submit" variant="primary" size="lg" loading={isPending} className="w-full">
        {isPending ? t.auth.creatingAccountBtn : t.auth.createAccountBtn}
      </Button>
      <p className="text-center text-xs text-fg-secondary">{t.auth.termsNotice}</p>
    </form.Form>
  );
}
