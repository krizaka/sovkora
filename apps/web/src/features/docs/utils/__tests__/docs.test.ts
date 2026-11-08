/** @jest-environment node */
import fs from "node:fs";
import path from "node:path";
import {
  JOURNEY_IDS,
  editUrl,
  listDocs,
  listPages,
  navItems,
  neighbours,
  plainText,
  prepareBody,
  readDoc,
  resolveLink,
  searchDocs,
  searchIndex,
  sourcesMap,
  splitSegments,
  tocOf,
} from "@/features/docs/utils/docs";
import { diagramData } from "@/features/docs/components/DocDiagram";
import { loadModel, parseSequence, parseStates } from "@/features/docs/utils/diagrams";

const sources = { "docs/CORE.md": "core", "docs/_generated/CLI.md": "cli", "SECURITY.md": "security" };

describe("published docs", () => {
  const docs = listDocs();

  it("lists the allow-listed docs, each with its markdown file and a journey", () => {
    // The essential only (2026-10-23): the internal inventories stay in the Sovkora repository.
    expect(docs.length).toBeGreaterThanOrEqual(14);
    for (const unpublished of ["tools", "automation", "jobs", "admin", "mobile-guide", "licensing"])
      expect(docs.map((d) => d.slug)).not.toContain(unpublished);
    expect(docs.length).toBeLessThan(25); // the short path: a page that repeats another is folded, not added
    for (const d of docs) {
      expect(fs.existsSync(path.join(process.cwd(), "content", "docs", d.file!))).toBe(true);
      expect(d.title).not.toBe("");
      expect(JOURNEY_IDS).toContain(d.journey);
    }
    expect(new Set(listPages().map((d) => d.slug)).size).toBe(listPages().length);
  });

  it("covers the three journeys, in reading order: quickstart, then concepts, then the guides", () => {
    const journeys = [...new Set(listPages().map((d) => d.journey))];
    expect(journeys).toEqual([...JOURNEY_IDS]);
    expect(
      listPages()
        .slice(0, 3)
        .map((d) => d.slug),
    ).toEqual(["101", "concepts", "self-hosting"]);
    // The reference is the last journey: folded out of the reading path.
    expect(JOURNEY_IDS.at(-1)).toBe("reference");
  });

  it("never publishes contributor-only or strategy docs, and no frontmatter is left behind", () => {
    const slugs = listPages().map((d) => d.slug);
    for (const internal of [
      "delivery",
      "billing_architecture",
      "production_readiness_audit",
      "architecture_assessment",
    ])
      expect(slugs).not.toContain(internal);
    for (const d of docs) {
      const { body } = readDoc(d.slug)!;
      expect(body.startsWith("---")).toBe(false);
      expect(body).not.toMatch(/^```mermaid/m);
    }
  });

  it("gives every page a previous and a next one, across journeys", () => {
    const pages = listPages();
    expect(neighbours(pages[0].slug).previous).toBeNull();
    expect(neighbours(pages[0].slug).next?.slug).toBe(pages[1].slug);
    expect(neighbours(pages.at(-1)!.slug).next).toBeNull();
    expect(navItems()).toHaveLength(pages.length);
  });

  it("sends 'edit' to the source file, and generated pages to the generated file", () => {
    expect(editUrl(listPages().find((d) => d.slug === "concepts")!)).toBe(
      "https://github.com/krizaka/orazaka/edit/main/docs/CONCEPTS.md",
    );
    expect(editUrl(listPages().find((d) => d.slug === "cli")!)).toBe(
      "https://github.com/krizaka/orazaka/blob/main/docs/_generated/CLI.md",
    );
    expect(editUrl(listPages().find((d) => d.slug === "mobile")!)).toBeNull();
  });

  it("returns null for an unknown doc", () => {
    expect(readDoc("nope")).toBeNull();
  });
});

describe("links of a doc", () => {
  it("keeps external and in-page links", () => {
    expect(resolveLink("https://x.dev/a", "docs/CORE.md", sources)).toEqual({
      href: "https://x.dev/a",
      internal: false,
    });
    expect(resolveLink("#section", "docs/CORE.md", sources)).toEqual({ href: "#section", internal: true });
  });

  it("resolves a link to a published doc to its page, whatever the relative path or case", () => {
    expect(resolveLink("CORE.md#kill-switch", "docs/101.md", sources)?.href).toBe("/docs/core#kill-switch");
    expect(resolveLink("_generated/CLI.md", "docs/101.md", sources)?.href).toBe("/docs/cli");
    expect(resolveLink("docs/core.md", "SECURITY.md", sources)?.href).toBe("/docs/core");
  });

  it("sends anything not published to GitHub — never a dead link — and drops a link out of the repository", () => {
    expect(resolveLink("BILLING_ARCHITECTURE.md", "docs/101.md", sources)).toEqual({
      href: "https://github.com/krizaka/orazaka/blob/main/docs/BILLING_ARCHITECTURE.md",
      internal: false,
    });
    expect(resolveLink("../../../elsewhere/x.md", "docs/101.md", sources)).toBeNull();
  });

  it("prepares a body: links, images, callouts — code fences untouched", () => {
    const body = [
      "See [core](CORE.md) and [out](../../nope.md).",
      "![shot](assets/a.png)",
      "> [!IMPORTANT]",
      "> Mind this.",
      "```md",
      "[core](CORE.md)",
      "```",
    ].join("\n");
    const out = prepareBody(body, "docs/101.md", sources);
    expect(out).toContain("[core](/docs/core)");
    expect(out).toContain("See [core](/docs/core) and out.");
    expect(out).toContain("![shot](https://raw.githubusercontent.com/krizaka/orazaka/main/docs/assets/a.png)");
    expect(out).toContain("> **Important.** Mind this.");
    expect(out).toContain("```md\n[core](CORE.md)\n```");
  });

  it("leaves no link of a published page pointing at a page that does not exist", () => {
    const slugs = new Set(listPages().map((d) => d.slug));
    for (const d of listDocs()) {
      const body = prepareBody(readDoc(d.slug)!.body, d.source, sourcesMap());
      for (const m of body.matchAll(/\]\(\/docs\/([a-z0-9-]+)/g)) expect(slugs).toContain(m[1]);
    }
  });
});

describe("segments and contents", () => {
  const body = [
    "# T",
    "",
    "## One",
    "",
    "```bash",
    "echo ok",
    "```",
    "",
    "```sequence",
    "participant a: A",
    "participant b: B",
    "a -> b: hi",
    "```",
    "",
    "## Two",
    "text",
  ].join("\n");

  it("splits the markdown from the schema blocks, keeping other fences in the markdown", () => {
    const s = splitSegments(body);
    expect(s.map((x) => x.kind)).toEqual(["markdown", "diagram", "markdown"]);
    expect(s[0]).toMatchObject({ kind: "markdown" });
    expect((s[0] as { text: string }).text).toContain("echo ok");
    expect(s[1]).toMatchObject({ kind: "diagram", lang: "sequence" });
  });

  it("lists the ## headings as the page's contents", () => {
    expect(tocOf(splitSegments(body)).map((h) => h.label)).toEqual(["One", "Two"]);
  });

  it("keeps the schema blocks as text for llms-full.txt", () => {
    expect(plainText(body)).toContain("(sequence)\nparticipant a: A");
  });
});

describe("the schema blocks of the published docs", () => {
  const model = loadModel();

  it("every one parses and names only modules of the generated model (a page that cannot be right does not build)", () => {
    let blocks = 0;
    for (const d of listDocs()) {
      for (const s of splitSegments(readDoc(d.slug)!.body)) {
        if (s.kind !== "diagram") continue;
        blocks++;
        expect(() => diagramData(s.lang, s.source, model)).not.toThrow();
      }
    }
    expect(blocks).toBeGreaterThanOrEqual(8);
  });

  it("refuses an unknown participant, an unclosed if, a module the model lacks", () => {
    expect(() => parseSequence("participant a: A\na -> z: hi", model)).toThrow(/unknown participant z/);
    expect(() => parseSequence("participant a: A\nif x\na -> a: y", model)).toThrow(/never closed/);
    expect(() => parseSequence("participant a: @no-such-module\na -> a: y", model)).toThrow(/not a module/);
    expect(() => parseStates("a -> b: x")).toThrow(/no transition from start/);
  });

  it("reads a state block in reading order", () => {
    const b = parseStates("start -> OPEN: created\nOPEN -> SOLD: ends\nSOLD -> end: done");
    expect(b.states.map((s) => s.id)).toEqual(["OPEN", "SOLD"]);
    expect(b.states[0].initial).toBe(true);
  });
});

describe("search", () => {
  const index = searchIndex();

  it("finds a page by its title, and a section by its heading", () => {
    expect(searchDocs(index, "install")[0].slug).toBe("self-hosting");
    expect(searchDocs(index, "cli")[0].slug).toBe("cli");
    expect(searchDocs(index, "workers").some((h) => h.slug === "concepts" && h.id)).toBe(true);
  });

  it("returns nothing for nothing", () => {
    expect(searchDocs(index, "   ")).toEqual([]);
    expect(searchDocs(index, "zzzqqq")).toEqual([]);
  });
});
