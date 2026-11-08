/**
 * One react-query in the app, the one @krizaka/query ships: a second copy gives the facade's hooks a
 * QueryClient context the app's provider never fills ("No QueryClient set", the build of #398).
 */
import { readFileSync } from "node:fs";
import { join } from "node:path";

const read = (file: string) =>
  JSON.parse(readFileSync(file, "utf8")) as {
    dependencies?: Record<string, string>;
    devDependencies?: Record<string, string>;
  };

const root = join(__dirname, "../../../..");
const app = read(join(root, "package.json"));
const facade = read(join(root, "node_modules/@krizaka/query/package.json"));

describe("@tanstack/* in the app", () => {
  const own = { ...app.dependencies, ...app.devDependencies };
  const tanstack = Object.keys(own).filter((name) => name.startsWith("@tanstack/"));

  it.each(tanstack)("%s is the exact version @krizaka/query pins", (name) => {
    expect(own[name]).toBe(facade.dependencies?.[name]);
  });
});
