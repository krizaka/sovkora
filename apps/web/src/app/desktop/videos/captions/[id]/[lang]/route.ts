import { LANGS, cuesVtt, isLang } from "@/core/clips/clips";
import { desktop } from "@/core/context/translations.desktop";
import { APP_VIDEOS, videoId } from "@/features/landing/utils/app-videos";

export const dynamic = "force-static";

const captioned = () => APP_VIDEOS.filter((v) => desktop.en.videos.items[videoId(v)]?.cues);

export const generateStaticParams = () => captioned().flatMap((v) => LANGS.map((lang) => ({ id: videoId(v), lang })));

/** `/desktop/videos/captions/<platform-flow>/<lang>`: the app video's WebVTT, written from the messages and the manifest's length. */
export async function GET(_request: Request, { params }: { params: Promise<{ id: string; lang: string }> }) {
  const { id, lang } = await params;
  const video = captioned().find((v) => videoId(v) === id);
  const cues = isLang(lang) ? desktop[lang].videos.items[id]?.cues : undefined;
  if (!video || !cues) {
    return new Response("Not found", { status: 404 });
  }
  return new Response(cuesVtt(cues, video.seconds), {
    headers: { "Content-Type": "text/vtt; charset=utf-8", "Cache-Control": "public, max-age=3600" },
  });
}
