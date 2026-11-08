import { Metadata } from "next";
import { Mascot } from "@krizaka/ui/mascots";
import { InterceptionForm } from "@/features/auth/components/InterceptionForm";

/**
 * Interception page metadata.
 */
export const metadata: Metadata = {
  title: "Required Action | Sovkora Workspace",
  description: "A required account setup or preference update form is pending completion.",
};

interface InterceptionPageProps {
  params: Promise<{ schemaId: string }>;
}

/**
 * Page routing component for active user interceptions.
 * Renders the dynamic layout form matching the target schema configuration.
 */
export default async function InterceptionPage({ params }: Readonly<InterceptionPageProps>) {
  const { schemaId } = await params;

  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-6 bg-gradient-to-tr from-surface-0 via-surface-1 to-surface-0 transition-colors duration-300">
      <div className="w-full max-w-xl">
        <h1 className="sr-only">Required Action - {schemaId}</h1>
        {/* The first thing a new account meets: the owl welcomes it — one mascot, at onboarding. */}
        <div className="mb-2 flex justify-center">
          <Mascot bird="owl" mood="welcome" size={96} />
        </div>
        <InterceptionForm schemaId={schemaId} />
      </div>
    </main>
  );
}
