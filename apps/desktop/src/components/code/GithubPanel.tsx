import { type ReactNode, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@krizaka/query";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { githubApi, errorMessage } from "../../lib/api";
import { format, useI18n } from "../../lib/i18n";
import { githubKeys, githubStatusQuery } from "../../lib/queries";

/**
 * The GitHub connection (ADR-093 slice 4): the device flow of the App « Orazaka for Mac ». The page shows the code to type
 * and where, and whether the person is connected; the tokens live in the Keychain on the Rust side and never reach here.
 */
export function GithubPanel() {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [waiting, setWaiting] = useState(false);
  const status = useQuery(githubStatusQuery(waiting));
  const refresh = () => queryClient.invalidateQueries({ queryKey: githubKeys.all });
  const onError = (e: unknown) => setError(errorMessage(e));
  const signIn = useMutation({ mutationFn: () => githubApi.signIn(), onMutate: () => setError(null), onSuccess: () => setWaiting(true), onError, onSettled: refresh });
  const signOut = useMutation({ mutationFn: () => githubApi.signOut(), onSuccess: () => setWaiting(false), onError, onSettled: refresh });

  const data = status.data;
  if (!data) return null;
  const pending = data.pending;
  const message = error ?? (!pending ? data.failure : null);

  let statusContent: ReactNode;
  if (data.connected) {
    statusContent = (
      <div className="space-y-1">
        <p className="text-sm">{t("code.github.connected")}</p>
        <Button variant="ghost" onClick={() => signOut.mutate()}>{t("code.github.signOut")}</Button>
        <p className="text-xs text-fg-secondary">{t("code.github.revokeHint")}</p>
      </div>
    );
  } else if (pending) {
    statusContent = (
      <div className="space-y-1">
        <p className="text-sm">{format(t("code.github.enter"), { uri: pending.verification_uri })}</p>
        <code className="block text-lg tracking-widest" data-testid="github-user-code">{pending.user_code}</code>
        <a className="text-sm underline" href={pending.verification_uri} target="_blank" rel="noreferrer">{t("code.github.open")}</a>
        <p className="text-xs text-fg-secondary">{t("code.github.waiting")}</p>
        <Button variant="ghost" onClick={() => signOut.mutate()}>{t("common.cancel")}</Button>
      </div>
    );
  } else {
    statusContent = (
      <Button variant="secondary" onClick={() => signIn.mutate()}>{t("code.github.connect")}</Button>
    );
  }

  return (
    <section className="space-y-2" aria-label={t("code.github.title")}>
      <h3 className="text-sm font-semibold">{t("code.github.title")}</h3>
      <p className="text-xs text-fg-secondary">{t("code.github.lead")}</p>
      {message && <Alert tone="danger">{message}</Alert>}
      {statusContent}
    </section>
  );
}
