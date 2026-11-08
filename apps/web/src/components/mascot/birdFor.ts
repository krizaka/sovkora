import type { MascotBird } from "@krizaka/ui/mascots";

/**
 * Which bird of the Krizaka flock speaks for a Studio or a job — by what the work is, so the same bird always comes
 * back for the same kind of work: the owl reads (documents, compliance), the duck builds (images, video, sound), the
 * pigeon delivers (prospection, follow-ups, notifications), the flamingo answers (wellbeing, conversation), the falcon
 * watches (security, plans and credits).
 */
const BY_KEY: ReadonlyArray<readonly [RegExp, MascotBird]> = [
  [/document|compliance|authenticity|validation|knowledge|analysis|transcri/, "owl"],
  [/image|video|speech|audio|visual|reel|showcase|media|compose/, "duck"],
  [/prospect|lead|follow|sequence|outbound|notification|delivery/, "pigeon"],
  [/journal|mood|session|wellbeing|bien-etre|chat|conversation/, "flamingo"],
  [/security|plan|billing|credit|pack/, "falcon"],
];

/** The bird for a Studio key, a pack key or a capability key (`orazaka.core.media.image`). Default: the owl. */
export function birdFor(key: string | null | undefined): MascotBird {
  const k = (key ?? "").toLowerCase();
  return BY_KEY.find(([pattern]) => pattern.test(k))?.[1] ?? "owl";
}
