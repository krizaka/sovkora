import { humanizeRole, roleName } from "../roles";

describe("roles shown as words", () => {
  it("drops the Spring prefix", () => {
    expect(roleName("ROLE_USER")).toBe("USER");
    expect(roleName("ADMIN")).toBe("ADMIN");
  });

  it("spaces and capitalises a role the catalogue has no word for", () => {
    expect(humanizeRole("ROLE_CONTENT_MODERATOR")).toBe("Content moderator");
    expect(humanizeRole("ROLE_QA")).toBe("Qa");
  });

  it("never returns an empty string", () => {
    expect(humanizeRole("ROLE_")).toBe("ROLE_");
  });
});

import { humanizeCode } from "../humanize";

describe("a code the catalogue has no word for", () => {
  it("is spaced and capitalised", () => {
    expect(humanizeCode("not-examined")).toBe("Not examined");
    expect(humanizeCode("INSUFFICIENT_EVIDENCE")).toBe("Insufficient evidence");
  });
});
