import { clips } from "@/core/context/translations.clips";
import { CLIP_IDS, LANGS, captionsVtt, isClipId, isLang } from "@/core/clips/clips";

export const dynamic = "force-static";

export const generateStaticParams = () => CLIP_IDS.flatMap((clip) => LANGS.map((lang) => ({ clip, lang })));

/** `/captions/<clip>/<lang>`: the clip's WebVTT, written from the messages (never a file to keep in step with them). */
export async function GET(_request: Request, { params }: { params: Promise<{ clip: string; lang: string }> }) {
  const { clip, lang } = await params;
  if (!isClipId(clip) || !isLang(lang)) return new Response("Not found", { status: 404 });
  return new Response(captionsVtt(clip, clips[lang][clip].cues), {
    headers: { "Content-Type": "text/vtt; charset=utf-8", "Cache-Control": "public, max-age=3600" },
  });
}
