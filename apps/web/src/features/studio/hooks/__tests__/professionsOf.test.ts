import { professionsOf } from "@/features/studio/hooks/useStudios";

describe("professionsOf", () => {
  it("lists each profession once, alphabetically, whatever the catalogue order", () => {
    const studios = ["tutor", "barber", "tutor", "Accountant", "barber"].map((profession) => ({ profession }));
    expect(professionsOf(studios)).toEqual(["Accountant", "barber", "tutor"]);
  });

  it("is empty for an empty catalogue", () => {
    expect(professionsOf([])).toEqual([]);
  });

  it("orders accented names by the alphabet, not by code unit", () => {
    expect(professionsOf([{ profession: "zoologue" }, { profession: "école" }, { profession: "avocat" }])).toEqual([
      "avocat",
      "école",
      "zoologue",
    ]);
  });
});
