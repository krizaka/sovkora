// The law a bake-off case reads, from a local copy of the official Données Québec statute file (Code civil du Québec, `CCQ-1991_<LANG>.xml`). The file is never in this repository
// (the texts are CC BY-NC-ND 4.0 and the rule is: link LégisQuébec, never paste an article); a case names articles, the corpus is read at run time from `--corpus`.
import { readFileSync } from "node:fs";
import { sha256 } from "./verify.mjs";

const decode = (s) =>
  s.replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&quot;", '"').replaceAll("&apos;", "'").replace(/&#(\d+);/gu, (_, n) => String.fromCodePoint(Number(n))).replaceAll("&amp;", "&");

const textOf = (xml) => decode(xml.replace(/<[^>]+>/gu, " ")).replace(/[ \t\r\n]+/gu, " ").trim();

/** The text of article `ref` of a statute XML: its label, then one paragraph per subsection (the shape the engine's passages have). */
export function articleFromXml(xml, ref) {
  const start = xml.indexOf(`<Section Code="se:${ref}"`);
  if (start < 0) return null;
  const end = xml.indexOf("</Section>", start);
  const body = xml.slice(start, end).replace(/<HistoricalNote>[\s\S]*?<\/HistoricalNote>/gu, "");
  const subsections = [...body.matchAll(/<Subsection\b[^>]*>([\s\S]*?)<\/Subsection>/gu)].map((m) => textOf(m[1]));
  const paragraphs = subsections.length ? subsections : [textOf(body.replace(/<Label[^>]*>[\s\S]*?<\/Label>/u, ""))];
  return `Article ${ref}\n\n${paragraphs.join("\n\n")}`;
}

/** Reads `refs` of `instrument` into the passages a retrieval hands the model: url (the official one), hash, title, text. */
export function passagesFrom(xmlPath, instrument, refs, language) {
  const xml = readFileSync(xmlPath, "utf8");
  return refs.map((ref) => {
    const text = articleFromXml(xml, ref);
    if (text === null) throw new Error(`article ${ref} is not in ${xmlPath}`);
    return {
      ref,
      url: `https://www.legisquebec.gouv.qc.ca/${language}/version/lc/${instrument}?code=se:${ref}`,
      title: `${instrument}, article ${ref}`,
      text,
      contentHash: sha256(text),
    };
  });
}
