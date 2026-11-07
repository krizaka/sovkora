import type { Metadata } from "next";
import { RunScreen } from "@/features/studio/components/RunScreen";

export const metadata: Metadata = {
  title: "Studio run | Sovkora",
  description: "Live progress and results of a Studio run.",
};

interface RunPageProps {
  params: Promise<{ runId: string }>;
}

/**
 * One run's live screen.
 *
 * A thin route shell: the progress is client state fed by the shared job stream.
 */
export default async function RunPage({ params }: Readonly<RunPageProps>) {
  const { runId } = await params;

  return (
    <main className="mx-auto w-full max-w-3xl px-4 py-6 sm:px-6">
      <RunScreen runId={runId} />
    </main>
  );
}
