import type { Metadata } from "next";
import { StudioDetail } from "@/features/studio/components/StudioDetail";

export const metadata: Metadata = {
  title: "Studio | Sovkora",
  description: "Install a ready-made workflow for your trade and run it on your own material.",
};

interface StudioDetailPageProps {
  params: Promise<{ studioKey: string }>;
}

/**
 * One Studio's detail and install action.
 *
 * A thin route shell: the screen is client-rendered because its install state,
 * dialog and entitlement copy all react to the signed-in user.
 */
export default async function StudioDetailPage({ params }: Readonly<StudioDetailPageProps>) {
  const { studioKey } = await params;

  return (
    <main className="mx-auto w-full max-w-3xl px-4 py-6 sm:px-6">
      <StudioDetail studioKey={studioKey} />
    </main>
  );
}
