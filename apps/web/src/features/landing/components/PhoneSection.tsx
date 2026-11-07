import { Clip } from "@/core/clips/Clip";
import type { ClipId } from "@/core/clips/clips";
import type { PhoneWords } from "@/core/context/translations.landing.types";
import { SectionHeading } from "@/features/landing/components/SectionHeading";

/**
 * The phone app, one recording beside its words: used by the home and the Download page so both say it the same way. The
 * clip is the shared `Clip` (captions, poster, plays on screen); the box is held to a phone's width so it does not tower.
 */
export function PhoneSection({
  id,
  words,
  testId,
}: Readonly<{ id: Extract<ClipId, `phone-${string}`>; words: PhoneWords; testId: string }>) {
  return (
    <article
      data-testid={testId}
      className="mx-auto grid max-w-6xl items-center gap-10 px-4 py-16 sm:px-5 md:grid-cols-[1fr_18rem] lg:py-20"
    >
      <SectionHeading eyebrow={words.eyebrow} title={words.title} lead={words.lead} />
      <Clip id={id} className="mx-auto w-full max-w-[18rem]" />
    </article>
  );
}
