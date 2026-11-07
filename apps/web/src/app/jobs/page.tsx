"use client";

import * as React from "react";
import { useQuery } from "@krizaka/query";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/core/hooks/useAuth";
import { AppShell } from "@/components/layout/AppShell";
import { useTranslation } from "@/core/context/LocaleContext";
import { useJobStream } from "@/core/context/JobStreamContext";
import { useJobs } from "@/features/jobs/hooks/useJobs";
import { Job } from "@/core/types/jobs.types";
import { Button } from "@krizaka/ui/button";
import { Loader } from "@krizaka/ui/loader";
import { Badge } from "@krizaka/ui/badge";
import { JobsTable } from "./JobsTable";
import { JobDeleteDialog } from "@/features/jobs/components/JobDeleteDialog";
import { useJobDeletion } from "@/features/jobs/hooks/useJobDeletion";
import { JobsKpiSummary } from "./JobsKpiSummary";
import { JobModal } from "./JobModal";
import { JobsGallery } from "./JobsGallery";
import { JobsRunningBanner } from "./JobsRunningBanner";
import { JobsEmpty } from "./JobsEmpty";
import { Suspense } from "react";
import { getSession } from "next-auth/react";
import { JOB_STATUS } from "@/core/constants/http.constants";

function JobsDashboardContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const targetJobId = searchParams.get("jobId");

  const { isAuthenticated, isLoading } = useAuth();
  const { t } = useTranslation();
  const { jobs: liveJobs, refreshJobs } = useJobStream();

  const [currentPage, setCurrentPage] = React.useState(0);
  const [pageSize, setPageSize] = React.useState(10);

  const {
    jobs: pageJobs,
    totalElements,
    isLoading: loading,
    refresh: refreshJobsQuery,
  } = useJobs(currentPage, pageSize, isAuthenticated);

  // The gallery reads further back than one page of the table: the last media generated, whatever ran since.
  const { jobs: recentJobs } = useJobs(0, 60, isAuthenticated);

  const [selectedJob, setSelectedJob] = React.useState<Job | null>(null);
  const [modalType, setModalType] = React.useState<"payload" | "result" | null>(null);

  const [dismissedJobId, setDismissedJobId] = React.useState<string | null>(null);
  const [copiedId, setCopiedId] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!isLoading && !isAuthenticated) router.push("/login");
  }, [isLoading, isAuthenticated, router]);

  const handleRefresh = async () => {
    await refreshJobs();
    refreshJobsQuery();
  };

  const deletion = useJobDeletion(() => void handleRefresh());

  const pageData = React.useMemo(() => {
    return pageJobs
      .map((j) => liveJobs.find((l) => l.id === j.id) || j)
      .filter((j) => !deletion.hidden.has(j.id) && j.status !== "DELETED");
  }, [pageJobs, liveJobs, deletion.hidden]);

  const running = React.useMemo(
    () => liveJobs.filter((j) => j.status === JOB_STATUS.PENDING || j.status === JOB_STATUS.PROCESSING),
    [liveJobs],
  );

  const handleCopy = (text: string, id: string) => {
    // Claimed as copied only once the browser accepted the clipboard write.
    navigator.clipboard.writeText(text).then(
      () => {
        setCopiedId(id);
        setTimeout(() => setCopiedId(null), 2000);
      },
      () => setCopiedId(null),
    );
  };

  const handleOpenModal = (job: Job, type: "payload" | "result") => {
    setSelectedJob(job);
    setModalType(type);
  };

  // The job named by `?jobId=` opens by itself: from the lists when it is there, else read from the API. Closing it
  // remembers the id, so a refreshed list never reopens what the operator dismissed.
  const knownJob = targetJobId
    ? (liveJobs.find((j) => j.id === targetJobId) ?? pageJobs.find((j) => j.id === targetJobId))
    : undefined;
  const fetchedJob = useQuery({
    queryKey: ["job", targetJobId],
    enabled: Boolean(targetJobId) && !knownJob,
    retry: false,
    queryFn: async () => {
      const session = await getSession();
      const headers: Record<string, string> = session?.user?.id ? { Authorization: `Bearer ${session.user.id}` } : {};
      const res = await fetch(`/api/v1/jobs/${targetJobId}`, { headers });
      if (!res.ok) throw new Error("Job not found");
      return (await res.json()) as Job;
    },
  });
  const autoJob = targetJobId && dismissedJobId !== targetJobId ? (knownJob ?? fetchedJob.data ?? null) : null;
  const shownJob = selectedJob ?? autoJob;
  const autoType = autoJob?.status === JOB_STATUS.COMPLETED ? "result" : "payload";
  const shownType = selectedJob ? modalType : autoJob ? autoType : null;

  if (isLoading || !isAuthenticated) {
    return (
      <section className="min-h-screen bg-surface-0">
        <Loader variant="page" label={t.insights.loading.jobs} />
      </section>
    );
  }

  return (
    <AppShell>
      <main className="flex-1 overflow-auto p-6 scrollbar-thin ambient-grid">
        <section className="mx-auto max-w-6xl space-y-6 animate-in fade-in slide-in-from-bottom-3 duration-300">
          <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
            <div className="flex flex-col gap-1">
              <h1 className="font-display text-2xl font-bold tracking-[-0.02em] text-fg">{t.jobs.title}</h1>
              <p className="text-sm text-fg-secondary">{t.jobs.subtitle}</p>
              <Badge tone="success" dot className="mt-1 self-start">
                {t.showcase.media.local}
              </Badge>
            </div>
            <Button variant="secondary" size="sm" onClick={handleRefresh} loading={loading}>
              {t.jobs.refresh}
            </Button>
          </header>

          <JobsRunningBanner jobs={running} history={recentJobs} />

          {!loading && totalElements === 0 && running.length === 0 ? (
            <JobsEmpty />
          ) : (
            <>
              <JobsGallery
                jobs={recentJobs.filter((j) => !deletion.hidden.has(j.id))}
                onOpen={(job) => handleOpenModal(job, "result")}
              />

              {/* AI Consumption KPIs */}
              <JobsKpiSummary jobs={pageData} />

              {/* Jobs Table */}
              <JobsTable
                jobs={pageData}
                loading={loading}
                totalElements={totalElements}
                currentPage={currentPage}
                pageSize={pageSize}
                onPageChange={setCurrentPage}
                onPageSizeChange={(size) => {
                  setPageSize(size);
                  setCurrentPage(0);
                }}
                onOpenModal={handleOpenModal}
                copiedId={copiedId}
                onCopy={handleCopy}
                onDelete={deletion.request}
              />
            </>
          )}
        </section>
      </main>
      <JobDeleteDialog job={deletion.asking} onConfirm={deletion.confirm} onDismiss={deletion.dismiss} />

      {/* JSON Payload Inspection Modal */}
      <JobModal
        key={`${shownJob?.id}-${shownType}`}
        job={shownJob}
        modalType={shownType}
        onClose={() => {
          setSelectedJob(null);
          setModalType(null);
          setDismissedJobId(targetJobId);
        }}
        copiedId={copiedId}
        onCopy={handleCopy}
      />
    </AppShell>
  );
}

export default function JobsDashboardPage() {
  return (
    <Suspense fallback={null}>
      <JobsDashboardContent />
    </Suspense>
  );
}
