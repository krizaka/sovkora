import React from "react";
import { FlatList, Pressable, StyleSheet, View } from "react-native";
import { useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { formatDistanceToNow, parseISO } from "date-fns";
import { enCA, frCA } from "date-fns/locale";
import { BarChart, DonutChart } from "@krizaka/charts/native";
import { Badge, Card, Header, Progress, Screen, Txt, type BadgeTone } from "@krizaka/ui/native";
import { JobPageSchema, type JobRecord, type JobStatus } from "@krizaka/orazaka-shared";
import { useApi } from "../core/useApi";
import { jobKind, jobsByKind, jobsPerDay } from "../core/jobStats";
import { Loading, LoadError, Empty } from "../components/AsyncState";
import { useI18n, type Key } from "../i18n";
import type { RootStackParamList } from "../navigation/AppNavigator";

// Partial: a status the kit adds later (CANCELLED, DELETED) reads as neutral instead of breaking the build.
export const JOB_TONE: Partial<Record<JobStatus, BadgeTone>> = {
  PENDING: "neutral",
  PROCESSING: "accent",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "neutral",
  DELETED: "neutral",
};

/** The two native charts above the list: the last 7 days by outcome, and the kinds of work. */
function JobsCharts({ jobs }: Readonly<{ jobs: readonly JobRecord[] }>): React.JSX.Element {
  const { t } = useI18n();
  return (
    <View style={styles.charts}>
      <Card.Root>
        <Card.Body>
          <BarChart
            title={t("jobs.perDay")}
            data={jobsPerDay(jobs, 7)}
            x="day"
            xFormat="day"
            series={[
              { key: "done", label: t("jobs.succeeded"), color: "accent" },
              { key: "failed", label: t("jobs.failed"), color: "danger" },
            ]}
            format="integer"
            stacked
            height={150}
          />
        </Card.Body>
      </Card.Root>
      <Card.Root>
        <Card.Body>
          <DonutChart
            title={t("jobs.byKind")}
            data={jobsByKind(jobs).map(({ kind, count }) => ({ kind: t(`jobs.kind.${kind}` as Key), count }))}
            label="kind"
            value="count"
            format="integer"
            height={150}
          />
        </Card.Body>
      </Card.Root>
    </View>
  );
}

/** One job: its kind, its state in the platform's badge, its progress while it runs, why it failed — one tap opens what it made. */
function JobCard({ job }: Readonly<{ job: JobRecord }>): React.JSX.Element {
  const { t, locale } = useI18n();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const running = job.status === "PENDING" || job.status === "PROCESSING";
  const kind = t(`jobs.kind.${jobKind(job.featureKey)}` as Key);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={t("jobs.open", { kind })}
      onPress={() => navigation.navigate("JobDetail", { jobId: job.id })}
    >
      <Card.Root>
        <Card.Body padding="sm" style={styles.job}>
          <View style={styles.header}>
            <Txt variant="label" numberOfLines={1} style={styles.kind}>
              {kind}
            </Txt>
            <Badge tone={JOB_TONE[job.status] ?? "neutral"} dot={running}>
              {t(`jobs.status.${job.status}` as Key)}
            </Badge>
          </View>
          <Txt variant="caption" tone="muted">
            {formatDistanceToNow(parseISO(job.createdAt), { addSuffix: true, locale: locale === "fr" ? frCA : enCA })}
          </Txt>
          {running && (
            <Progress
              size="sm"
              label={t("jobs.running", { kind })}
              value={job.progress && job.progress > 0 ? job.progress : null}
            />
          )}
          {job.status === "FAILED" ? (
            <Txt variant="caption" tone="danger">
              {t("jobs.failedHint")}
            </Txt>
          ) : null}
        </Card.Body>
      </Card.Root>
    </Pressable>
  );
}

/**
 * The actor's asynchronous jobs, newest first, under two native charts of the same jobs (@krizaka/charts/native).
 *
 * Read-only on mobile: approving or purging a job is a console action, and putting a destructive
 * button on a phone screen is how a job gets purged from a pocket.
 */
export function JobsScreen(): React.JSX.Element {
  const { t } = useI18n();
  const { data, isLoading, error, reload } = useApi<unknown>("/api/v1/jobs?page=0&size=50");

  const body = (): React.JSX.Element => {
    if (isLoading) return <Loading />;
    if (error !== null) return <LoadError message={error} onRetry={reload} />;
    const page = JobPageSchema.safeParse(data);
    const jobs = page.success ? page.data.content : [];
    if (jobs.length === 0) return <Empty message={t("common.emptyJobs")} />;
    return (
      <FlatList
        contentContainerStyle={styles.listContent}
        data={jobs}
        keyExtractor={(job) => job.id}
        ListHeaderComponent={<JobsCharts jobs={jobs} />}
        renderItem={({ item }) => <JobCard job={item} />}
      />
    );
  };

  return (
    <Screen scroll={false} header={<Header title={t("tabs.jobs")} />}>
      {body()}
    </Screen>
  );
}

const styles = StyleSheet.create({
  listContent: { padding: 16, gap: 10 },
  charts: { gap: 10, marginBottom: 6 },
  job: { gap: 6 },
  header: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: 8 },
  kind: { flexShrink: 1 },
});
