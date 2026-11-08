import { parseInline, parseMarkdown } from "../markdown";

describe("parseInline", () => {
  it("turns bold, italic, code and links into spans — no literal markers left", () => {
    const spans = parseInline("**Nom :** Atelier *Morel* a `id` [site](https://x.fr)");
    expect(spans).toEqual([
      { kind: "bold", text: "Nom :" },
      { kind: "text", text: " Atelier " },
      { kind: "italic", text: "Morel" },
      { kind: "text", text: " a " },
      { kind: "code", text: "id" },
      { kind: "text", text: " " },
      { kind: "link", text: "site", href: "https://x.fr" },
    ]);
    expect(JSON.stringify(spans)).not.toContain("**");
  });

  it("leaves an unclosed marker as text (a streaming answer)", () => {
    expect(parseInline("a **b")).toEqual([{ kind: "text", text: "a **b" }]);
  });
});

describe("parseMarkdown", () => {
  it("reads headings, paragraphs joined across lines, and lists", () => {
    const blocks = parseMarkdown("# Titre\n\nUne ligne\nsuite\n\n- un\n- **deux**\n\n1. a\n2. b");
    expect(blocks.map((b) => b.kind)).toEqual(["heading", "paragraph", "list", "list"]);
    expect(blocks[1]).toEqual({ kind: "paragraph", inline: [{ kind: "text", text: "Une ligne suite" }] });
    expect(blocks[2]).toMatchObject({ kind: "list", ordered: false });
    expect((blocks[2] as unknown as { items: unknown[] }).items).toHaveLength(2);
    expect(blocks[3]).toMatchObject({ kind: "list", ordered: true });
  });

  it("caps heading levels at 3 and reads quotes and rules", () => {
    const blocks = parseMarkdown("###### Petit\n> cité\n---");
    expect(blocks[0]).toMatchObject({ kind: "heading", level: 3 });
    expect(blocks[1]?.kind).toBe("quote");
    expect(blocks[2]?.kind).toBe("rule");
  });

  it("keeps a code fence verbatim, even left open by a stream", () => {
    expect(parseMarkdown("```\na **b**\n```")).toEqual([{ kind: "code", text: "a **b**" }]);
    expect(parseMarkdown("```\nvar x")).toEqual([{ kind: "code", text: "var x" }]);
  });
});
