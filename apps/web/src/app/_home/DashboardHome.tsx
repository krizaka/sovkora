"use client";

import * as React from "react";
import { format } from "date-fns";
import { fr, enUS } from "date-fns/locale";
import { useRouter } from "next/navigation";
import { Skeleton, Icon } from "@krizaka/orazaka-design-system";
import { useAuth } from "@/core/hooks/useAuth";
import { Greeting } from "@/core/components/Greeting";
import { AppShell } from "@/components/layout/AppShell";
import { useTranslation } from "@/core/context/LocaleContext";
import { useChatStream } from "@/features/chat-session/hooks/useChatStream";
import { useWallet } from "@/features/billing/hooks/useWallet";
import { InterceptorPipeline } from "@/features/dashboard/components/InterceptorPipeline";
import { RecentSessions } from "@/features/dashboard/components/RecentSessions";
import { JobsGallery } from "@/app/jobs/JobsGallery";
import { CreditsMeter } from "@/features/billing/components/CreditsMeter";
import { usePlan } from "@/features/billing/hooks/usePlan";
import { ActivityCharts } from "@/features/insights/components/ActivityCharts";
import { UsageExport } from "@/features/insights/components/UsageControls";
import { UsageKpis } from "@/features/insights/components/UsageKpis";
import { systemNow } from "@/core/jobs/systemClock";
import { usageRows, usageSummary } from "@/core/jobs/usageStats";
import { StudiosUsedChart } from "@/features/insights/components/UsageCharts";
import { useInsights } from "@/features/insights/hooks/useInsights";
import { useStudios } from "@/features/studio/hooks/useStudios";
import { jobMediaUrl } from "@/core/jobs/jobMedia";
import { ChatIcon } from "@krizaka/icons";
import { Button } from "@krizaka/ui/button";
import Link from "next/link";

const DATE_LOCALES = { fr, en: enUS } as const;
/** The dashboard reads the last two weeks of the person's own work. */
const DASHBOARD_DAYS = 14;

/**
 * The signed-in home: greeting, the interceptor pipeline (admins), the workspace's real figures, the credits and
 * the charts of the person's own work (jobs per day, kinds, Studios used), the latest creations, quick actions and
 * the latest conversations.
 */
export function DashboardHome() {
  const router = useRouter();
  const { isAuthenticated, isLoading, user } = useAuth();
  const { locale, t } = useTranslation();
  const { threads } = useChatStream("dashboard");
  const { wallet, available, isLoading: walletLoading } = useWallet();
  const days = DASHBOARD_DAYS;
  const { jobs, runs, isLoading: insightsLoading, hasError } = useInsights(isAuthenticated);
  const { plan } = usePlan(isAuthenticated);
  const { studios } = useStudios(null);
  const summary = React.useMemo(() => usageSummary(jobs, runs, days, systemNow()), [jobs, runs, days]);
  const period = React.useMemo(() => usageRows(jobs, runs, days, systemNow()), [jobs, runs, days]);
  const studioLabel = React.useCallback(
    (key: string) => studios.find((s) => s.studioKey === key)?.label ?? key,
    [studios],
  );

  React.useEffect(() => {
    if (!isLoading && !isAuthenticated) router.push("/login");
  }, [isLoading, isAuthenticated, router]);

  // Deferred creation: land in the empty composer; the thread is persisted only
  // once the first message is sent (avoids empty threads).
  const handleStartNewChat = () => router.push("/chat");

  const formatDate = (timestamp: number) => {
    try {
      return format(timestamp, "MMM d, HH:mm", {
        locale: DATE_LOCALES[locale],
      });
    } catch {
      return "";
    }
  };

  // Time-of-day greeting icon (SVG, not emoji — governance §4/§8).
  const greetingIcon = new Date().getHours() < 18 ? "sun" : "moon";

  if (isLoading || !isAuthenticated) {
    return (
      <section className="flex min-h-screen items-center justify-center bg-surface-0 ambient-grid">
        <article className="w-full max-w-6xl px-6 space-y-8">
          <Skeleton variant="text" width="40%" height="2rem" />
          <Skeleton variant="rect" height="5rem" />
          <section className="grid gap-6 md:grid-cols-3">
            <Skeleton variant="rect" height="8rem" />
            <Skeleton variant="rect" height="8rem" />
            <Skeleton variant="rect" height="8rem" />
          </section>
        </article>
      </section>
    );
  }

  const recentThreads = [...(threads || [])].sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 3);

  return (
    <AppShell>
      <main className="flex-1 overflow-auto p-6 scrollbar-thin ambient-grid">
        <article className="mx-auto max-w-6xl space-y-6 stagger-children">
          <header className="flex flex-wrap items-center justify-between gap-3">
            <section className="space-y-1">
              <h2 className="fluid-2xl font-bold tracking-tight text-fg flex items-center gap-2">
                <Icon name={greetingIcon} className="h-6 w-6 text-accent" />
                <span>
                  <Greeting />
                </span>
              </h2>
              <p className="text-fg-secondary fluid-sm">{t.dashboard.overview}</p>
            </section>
            <Button variant="primary" size="md" onClick={handleStartNewChat}>
              <ChatIcon size={16} />
              {t.dashboard.startNewChat}
            </Button>
          </header>

          {user?.role === "admin" && <InterceptorPipeline />}

          <UsageKpis summary={summary} days={days} loading={insightsLoading} />

          <ActivityCharts summary={summary} jobs={period.jobs} days={days} loading={insightsLoading} error={hasError} />

          <section className="grid gap-4 lg:grid-cols-2">
            <span className="flex flex-col gap-2">
              <CreditsMeter wallet={wallet} available={available} plan={plan} loading={walletLoading} href="/usage" />
              <span className="flex justify-end">
                <UsageExport jobs={jobs} runs={runs} days={days} />
              </span>
            </span>
            <StudiosUsedChart runs={period.runs} studioLabel={studioLabel} loading={insightsLoading} error={hasError} />
          </section>

          {jobs.some((job) => jobMediaUrl(job)) && (
            <section className="flex flex-col gap-3">
              <header className="flex items-center justify-between px-1">
                <h3 className="text-xs font-semibold text-fg-muted">{t.insights.usage.recent}</h3>
                <Button asChild variant="ghost" size="sm">
                  <Link href="/jobs">{t.showcase.dashboard.seeAll}</Link>
                </Button>
              </header>
              <JobsGallery jobs={jobs} limit={3} onOpen={(job) => router.push(`/jobs?jobId=${job.id}`)} />
            </section>
          )}

          <section className="grid gap-6">
            <RecentSessions
              recentThreads={recentThreads}
              onStartNewChat={handleStartNewChat}
              onResumeSession={(id) => router.push(`/chat?conversationId=${id}`)}
              formatDate={formatDate}
              t={t}
            />
          </section>
        </article>
      </main>
    </AppShell>
  );
}
