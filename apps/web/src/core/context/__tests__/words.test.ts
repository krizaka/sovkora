import { localise, w } from "@/core/context/words";

describe("words: one tree, two languages", () => {
  const tree = {
    title: w("Hello", "Bonjour"),
    items: [w("One", "Un"), w("Two", "Deux")],
    nested: { label: w("Yes", "Oui") },
  };

  it("reads the English side", () => {
    expect(localise(tree, 0)).toEqual({ title: "Hello", items: ["One", "Two"], nested: { label: "Yes" } });
  });

  it("reads the French side with the same shape", () => {
    expect(localise(tree, 1)).toEqual({ title: "Bonjour", items: ["Un", "Deux"], nested: { label: "Oui" } });
  });
});
