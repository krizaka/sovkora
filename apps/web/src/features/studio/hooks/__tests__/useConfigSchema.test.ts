import { renderHook } from "@testing-library/react";
import { useConfigSchema } from "@/features/studio/hooks/useConfigSchema";

const keys = (schema: string | null) => renderHook(() => useConfigSchema(schema)).result.current.map((f) => f.key);

describe("useConfigSchema", () => {
  it("reads a JSON Schema object by its properties: an empty one asks nothing (the Studios of dev serve this shape)", () => {
    expect(keys('{"type": "object", "properties": {}}')).toEqual([]);
    expect(keys('{"type":"object","properties":{"brandName":{"type":"string","title":"Agency name"}}}')).toEqual([
      "brandName",
    ]);
  });

  it("still reads the flat map of fields a blueprint may declare", () => {
    expect(
      keys(
        '{"brandName":{"type":"string","title":"Agency name"},"tone":{"enum":["premium","direct"],"default":"premium"}}',
      ),
    ).toEqual(["brandName", "tone"]);
  });

  it("asks nothing without a schema or with one it cannot read", () => {
    expect(keys(null)).toEqual([]);
    expect(keys("not json")).toEqual([]);
  });
});
