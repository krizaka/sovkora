import { parseInputSchema } from "../inputSchema";

const schema = (properties: Record<string, unknown>) =>
  JSON.stringify({ type: "object", required: Object.keys(properties).slice(0, 1), properties });

describe("a Studio input that takes a document", () => {
  it("is read from the standard JSON-Schema words: a base64 string whose media type is application/pdf", () => {
    const [field] = parseInputSchema(
      schema({
        file: { type: "string", contentMediaType: "application/pdf", contentEncoding: "base64", maxLength: 1_400_000 },
      }),
    );
    expect(field?.documentKind).toBe("pdf");
    expect(field?.maxLength).toBe(1_400_000);
  });

  it("is read from the document-validation pack's own `documentBase64` while it declares nothing more", () => {
    const [field] = parseInputSchema(
      schema({ documentBase64: { type: "string", maxLength: 1_400_000, title: "Le fichier PDF" } }),
    );
    expect(field?.documentKind).toBe("pdf");
  });

  it("is not a document when it is a prompt, an image or a number", () => {
    const fields = parseInputSchema(
      schema({
        prompt: { type: "string" },
        image: { type: "string", format: "asset:image" },
        seed: { type: "integer" },
      }),
    );
    expect(fields.map((f) => f.documentKind)).toEqual([undefined, undefined, undefined]);
  });
});
