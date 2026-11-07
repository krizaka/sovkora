import { LoginEntry } from "@/features/auth/components/LoginEntry";

/**
 * `/login` — the app's real sign-in screen. While `AUTH_LOGIN_ENABLED` is off (default) it is shown disabled under a
 * calm banner and nothing calls the backend; when on, the sign-in works as it always did.
 */
export default function LoginPage() {
  return <LoginEntry />;
}
