import React from "react";
import { StyleSheet, View } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { formatDistanceToNow, parseISO } from "date-fns";
import { enCA, frCA } from "date-fns/locale";
import { Badge, Header, Progress, Screen, Txt } from "@krizaka/ui/native";
import { JobSchema } from "@krizaka/orazaka-shared";
import type { RootStackParamList } from "../navigation/AppNavigator";
import { useApi } from "../core/useApi";
import { useToken } from "../core/AuthContext";
import { jobArtefacts } from "../core/jobArtefacts";
import { jobKind } from "../core/jobStats";
import { ArtefactCard } from "../components/ArtefactCard";
import { Loading, LoadError } from "../components/AsyncState";
import { useI18n, type Key } from "../i18n";
import { JOB_TONE } from "./JobsScreen";

type Props = NativeStackScreenProps<RootStackParamList, "JobDetail">;

/**
 * One job, opened from the list: what it made — the picture, the video, the voice, the text, with the same cards as a
 * Studio run (ArtefactCard) — its state, and why it failed in a sentence (never the platform's stack trace).
 */
export function JobDetailScreen({ route, navigation }: Props): React.JSX.Element {
  const { t, locale } = useI18n();
  const token = useToken();
  const { data, isLoading, error, reload } = useApi<unknown>(`/api/v1/jobs/${encodeURIComponent(route.params.jobId)}`);
  const parsed = JobSchema.safeParse(data);
  const job = parsed.success ? parsed.data : null;
  const kind = job ? t(`jobs.kind.${jobKind(job.featureKey)}` as Key) : t("tabs.jobs");
  const header = <Header title={kind} size="inline" backLabel={t("common.back")} onBack={() => navigation.goBack()} />;

  if (isLoading)
    return (
      <Screen header={header}>
        <Loading />
      </Screen>
    );
  if (error !== null || job === null)
    return (
      <Screen header={header}>
        <LoadError message={error ?? t("common.unavailable")} onRetry={reload} />
      </Screen>
    );

  const running = job.status === "PENDING" || job.status === "PROCESSING";
  const artefacts = jobArtefacts(job);
  return (
    <Screen header={header} contentStyle={styles.content}>
      <View style={styles.status}>
        <Badge tone={JOB_TONE[job.status] ?? "neutral"} dot={running} pulse={running}>
          {t(`jobs.status.${job.status}` as Key)}
        </Badge>
        <Txt variant="caption" tone="muted">
          {formatDistanceToNow(parseISO(job.createdAt), { addSuffix: true, locale: locale === "fr" ? frCA : enCA })}
        </Txt>
      </View>
      {running && (
        <Progress label={t("jobs.running", { kind })} value={job.progress && job.progress > 0 ? job.progress : null} />
      )}
      {artefacts.map((artefact) => (
        <ArtefactCard key={artefact.key} artefact={artefact} token={token} />
      ))}
      {job.status === "FAILED" && (
        <Txt tone="danger" variant="caption">
          {t("jobs.failedHint")}
        </Txt>
      )}
      {!running && job.status !== "FAILED" && artefacts.length === 0 && <Txt tone="muted">{t("jobs.nothing")}</Txt>}
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { gap: 16 },
  status: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
});
