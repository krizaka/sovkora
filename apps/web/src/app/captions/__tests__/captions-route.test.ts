/** @jest-environment node */
import { CLIP_IDS, LANGS } from "@/core/clips/clips";
import { GET } from "@/app/captions/[clip]/[lang]/route";

/** krizaka-internal#3935: a caption said « {brand} routes the request. » — the placeholder was never filled. */
describe("the captions of every clip", () => {
  for (const clip of CLIP_IDS) {
    for (const lang of LANGS) {
      it(`${clip}/${lang} carry no {brand} placeholder`, async () => {
        const response = await GET(new Request("http://x"), { params: Promise.resolve({ clip, lang }) });
        expect(response.status).toBe(200);
        expect(await response.text()).not.toContain("{brand}");
      });
    }
  }
});
