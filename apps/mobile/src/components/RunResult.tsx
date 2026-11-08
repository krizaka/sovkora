import React from "react";
import { StyleSheet, View } from "react-native";
import { Badge, Button, Progress, Txt } from "@krizaka/ui/native";
import type { Run } from "@krizaka/orazaka-shared";
import { useI18n, type Key } from "../i18n";
import { ArtefactCard } from "./ArtefactCard";

type Props = Readonly<{
  run: Run;
  live: boolean;
  done: number;
  percent: number | null;
  token: string | null;
  onRunAgain: () => void;
}>;

/** The badge tone of a run status. */
function statusTone(status: Run["status"]): "success" | "danger" | "accent" {
  if (status === "SUCCEEDED") return "success";
  return status === "FAILED" || status === "CANCELLED" ? "danger" : "accent";
}

/** What a started run shows: its status, its progress while live, its outputs, and the way to run again. */
export function RunResult({ run, live, done, percent, token, onRunAgain }: Props): React.JSX.Element {
  const { t } = useI18n();
  return (
    <>
      <View style={styles.status}>
        <Badge tone={statusTone(run.status)} dot={live} pulse={live}>
          {t(`studios.run.${run.status}` as Key)}
        </Badge>
        {run.steps.length > 0 && (
          <Txt variant="caption" tone="secondary">
            {t("studios.stepsDone", { done, total: run.steps.length })}
          </Txt>
        )}
      </View>
      {live && (
        <Progress
          label={t("studios.progress")}
          value={percent}
          valueText={percent === null ? t("studios.generating") : `${String(percent)}%`}
        />
      )}

      {run.outputs.length > 0 && (
        <View style={styles.outputs}>
          {run.outputs.map((artefact) => (
            <ArtefactCard key={artefact.key} artefact={artefact} token={token} />
          ))}
        </View>
      )}

      {/* The platform's own error text is a stack trace: it is never shown, the person gets a sentence they can act on. */}
      {run.status === "FAILED" && (
        <Txt tone="danger" variant="caption">
          {t("studios.failedHint")}
        </Txt>
      )}
      {!live && <Button variant="secondary" label={t("studios.runAgain")} onPress={onRunAgain} />}
    </>
  );
}

const styles = StyleSheet.create({
  status: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  outputs: { gap: 12 },
});
