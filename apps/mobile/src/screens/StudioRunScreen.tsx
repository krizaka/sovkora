import React, { useCallback, useEffect, useRef, useState } from "react";
import { StyleSheet } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { Button, Header, Screen, Txt } from "@krizaka/ui/native";
import { isTerminalRun } from "@krizaka/orazaka-shared";
import type { Run } from "@krizaka/orazaka-shared";
import type { RootStackParamList } from "../navigation/AppNavigator";
import { useToken } from "../core/AuthContext";
import { apiRequest } from "../config/api";
import { useApi } from "../core/useApi";
import { StudioField } from "../components/StudioField";
import { RunResult } from "../components/RunResult";
import { isComplete, parseInputSchema, toInputs, type SchemaField } from "../core/inputSchema";
import { initialAnswers, runPercent, splitFields } from "../core/runForm";
import { fieldWords } from "../core/fieldWords";
import { errorText } from "../i18n/errorText";
import { useI18n } from "../i18n";

/** Where a run starts: an installed Studio's installation, or — a TOOLKIT Studio has none — its own key. */
export const runPath = (installationId: string | null, studioKey: string): string =>
  installationId === null
    ? `/api/v1/studios/${studioKey}/runs`
    : `/api/v1/studios/installations/${installationId}/runs`;

/** How often a live run is re-read. The DAG advances on job outcomes, not on a clock. */
const POLL_MS = 2000;

type Props = NativeStackScreenProps<RootStackParamList, "StudioRun">;

/**
 * Starts a run of an installed Studio and follows it to its end.
 *
 * Polling, not SSE: the run stream is a server-side saga whose progress lands in the run row, and
 * a native app that backgrounds and resumes recovers from a poll for free — where a dropped
 * EventSource would need reconnection logic for a screen that finishes in minutes.
 */
export function StudioRunScreen({ route, navigation }: Props): React.JSX.Element {
  const { installationId = null, label: named, studioKey } = route.params;
  const label = named ?? studioKey; // opened by address (orazaka://studios/<key>/run): the key, until the Studio says its name
  const { t, locale } = useI18n();
  const bearer = useToken();
  // The blueprint declares what a run needs; asking the server beats hardcoding a form per
  // Studio, and beats sending {} and letting the run be refused with a 400 the actor cannot act on.
  const detail = useApi<{ inputSchema: string | null }>(`/api/v1/studios/${studioKey}?locale=${locale}`);
  const fields = parseInputSchema(detail.data?.inputSchema);
  const { basic, advanced } = splitFields(fields);
  const [typed, setTyped] = useState<Record<string, string>>({});
  const [showAdvanced, setShowAdvanced] = useState(false);
  // A field with a default (the video Studio's `aspect`) starts on it; what the person chose wins.
  const answers = { ...initialAnswers(fields), ...typed };
  const [run, setRun] = useState<Run | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isStarting, setIsStarting] = useState(false);
  // Photos being sent: the run waits for them (an asset id that is not there yet is a run started without its image).
  const [sending, setSending] = useState<Record<string, boolean>>({});
  const isSending = Object.values(sending).some(Boolean);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (timer.current !== null) {
      clearInterval(timer.current);
      timer.current = null;
    }
  }, []);

  const refresh = useCallback(
    async (runId: string) => {
      try {
        const next = await apiRequest<Run>(`/api/v1/studios/runs/${runId}`, { token: bearer });
        setRun(next);
        if (isTerminalRun(next.status)) {
          stopPolling();
        }
      } catch (cause: unknown) {
        setError(errorText(cause, t, "studios.followFailed"));
        stopPolling();
      }
    },
    [bearer, stopPolling, t],
  );

  const start = async () => {
    setIsStarting(true);
    setError(null);
    try {
      const started = await apiRequest<Run>(runPath(installationId, studioKey), {
        method: "POST",
        token: bearer,
        body: { inputs: toInputs(fields, answers) },
      });
      setRun(started);
      if (!isTerminalRun(started.status)) {
        timer.current = setInterval(() => void refresh(started.id), POLL_MS);
      }
    } catch (cause: unknown) {
      setError(errorText(cause, t, "studios.startFailed"));
    } finally {
      setIsStarting(false);
    }
  };

  // A screen left behind must not keep polling — that is a request every two seconds, forever.
  useEffect(() => stopPolling, [stopPolling]);

  const { titleOf, choiceLabel } = fieldWords(t, studioKey);

  /** The setter of one field's answer. */
  const answerOf = (key: string) => (value: string) => setTyped((prev) => ({ ...prev, [key]: value }));
  /** The setter of one field's "being sent" flag. */
  const busyOf = (key: string) => (busy: boolean) =>
    setSending((prev) => (prev[key] === busy ? prev : { ...prev, [key]: busy }));

  const renderField = (field: SchemaField): React.JSX.Element => (
    <StudioField
      key={field.key}
      field={field}
      title={titleOf(field)}
      choiceLabel={choiceLabel}
      value={answers[field.key] ?? ""}
      disabled={isStarting}
      token={bearer}
      onAnswer={answerOf(field.key)}
      onBusy={busyOf(field.key)}
    />
  );

  const percent = run === null ? null : runPercent(run.steps);
  const done = run?.steps.filter((s) => s.status === "SUCCEEDED" || s.status === "SKIPPED").length ?? 0;
  const live = run !== null && !isTerminalRun(run.status);

  return (
    <Screen
      header={<Header title={label} size="inline" backLabel={t("common.back")} onBack={() => navigation.goBack()} />}
      contentStyle={styles.content}
    >
      {run === null ? (
        <>
          {basic.map(renderField)}
          {advanced.length > 0 && (
            <>
              <Button
                variant="ghost"
                size="sm"
                label={showAdvanced ? t("studios.fewerOptions") : t("studios.moreOptions")}
                onPress={() => setShowAdvanced((v) => !v)}
              />
              {showAdvanced && advanced.map(renderField)}
            </>
          )}
          <Button
            testID="run-launch"
            variant="primary"
            label={t("studios.launch")}
            size="lg"
            loading={isStarting}
            disabled={!isComplete(fields, answers) || isSending}
            onPress={() => void start()}
          />
        </>
      ) : (
        <RunResult
          run={run}
          live={live}
          done={done}
          percent={percent}
          token={bearer}
          onRunAgain={() => {
            setRun(null);
            setError(null);
          }}
        />
      )}

      {error !== null && (
        <Txt tone="danger" variant="caption">
          {error}
        </Txt>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { gap: 16 },
});
