import { landing } from "@/core/context/translations.landing";

const IDS = ["documents", "models", "tools", "agents", "studios", "runs"] as const;

describe("the home's « What Sovkora does » section", () => {
  it.each(["en", "fr"] as const)("%s: six items, each with its industry term, no absolute claim", (locale) => {
    const c = landing[locale].capabilities;
    expect(Object.keys(c.items).sort()).toEqual([...IDS].sort());
    const words = IDS.map((id) => `${c.items[id].title} ${c.items[id].text}`).join(" ");
    expect(words).not.toMatch(/absolu|absolute|100 ?%|garanti|guarantee|apache/i);
    expect(c.items.documents.tag).toBe("RAG");
    expect(c.items.models.tag).toBe("LLM");
    expect(c.items.tools.tag).toBe("MCP");
  });

  it("RAG, not shipped end to end, says it is in development in both languages", () => {
    expect(landing.fr.capabilities.items.documents.text).toMatch(/en développement/);
    expect(landing.en.capabilities.items.documents.text).toMatch(/in development/);
  });

  it("a hosted model is said to send requests to its provider", () => {
    expect(landing.fr.capabilities.items.models.text).toMatch(/fournisseur/);
    expect(landing.en.capabilities.items.models.text).toMatch(/provider/);
  });
});
