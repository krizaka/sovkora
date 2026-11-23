import { cn } from "@krizaka/ui/cn";
import { Clip } from "@/core/clips/Clip";
import { CLIPS } from "@/core/clips/clips";
import { sectionMedia } from "@/core/clips/media-registry";

/**
 * The media of one page section, by role (`content/media-registry.json`): each recording the registry gives that section, with
 * its caption. Nothing when the registry lists none. A phone recording is held to a phone's width so it does not tower.
 */
export function SectionMedia({
  page,
  role,
  priority = false,
  className,
}: Readonly<{ page: string; role: string; priority?: boolean; className?: string }>) {
  const ids = sectionMedia(page, role);
  if (ids.length === 0) {
    return null;
  }
  return (
    <div className={cn("flex flex-col gap-4", className)} data-testid={`media-${page}-${role}`}>
      {ids.map((id) => (
        <Clip
          key={id}
          id={id}
          priority={priority}
          className={cn(CLIPS[id].height > CLIPS[id].width && "mx-auto w-full max-w-[18rem]")}
        />
      ))}
    </div>
  );
}
