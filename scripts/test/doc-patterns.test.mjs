import { test } from "node:test";
import assert from "node:assert/strict";
import {
  BRACE_PLACEHOLDER, CLI_PORT, COMMENT_LINE, CONVERT_AND_SEND, FIRST_JAVADOC_LINE, GATEWAY_ROUTE, JAVADOC_INLINE_TAG, KEY_BUILDER,
  PARAM_TAG, PATH_VARIABLE, PROPERTY_REF, RABBIT_LISTENER, TRAILING_NUMBER, WORKER_BINDING, YAML_KEY_LINE,
  logSteps, paramOf, splitConcatenation, splitStepText, stripBlockComments,
} from "../lib/doc-patterns.mjs";

const hostile = (unit, n = 50000) => unit.repeat(n);
function fast(fn) {
  const t = Date.now();
  fn();
  assert.ok(Date.now() - t < 1000, "must finish in under a second");
}

test("gateway route", () => {
  const yml = "- path-prefix: /api/x \n  target: ${EDGE_URL:http://localhost:8088}";
  const m = [...yml.matchAll(GATEWAY_ROUTE)][0];
  assert.deepEqual([m[1], m[2]], ["/api/x", "8088"]);
  fast(() => [...(`- path-prefix: a` + hostile(" ")).matchAll(GATEWAY_ROUTE)]);
});

test("key builder", () => {
  const m = [...'static String k(String a) { return  "x." + a; }'.matchAll(KEY_BUILDER)][0];
  assert.deepEqual([m[1], m[2]], ["k", '"x." + a']);
  fast(() => [...("static String k(String a) {return" + hostile(" ")).matchAll(KEY_BUILDER)]);
});

test("rabbit listener and convertAndSend", () => {
  assert.equal([...'@RabbitListener(queues = Q.NAME )'.matchAll(RABBIT_LISTENER)][0][1].trim(), "Q.NAME");
  assert.equal([...'@RabbitListener(queues = {A, B} , x)'.matchAll(RABBIT_LISTENER)][0][1], "{A, B}");
  fast(() => [...("@RabbitListener(queues = a" + hostile(" b")).matchAll(RABBIT_LISTENER)]);
  const m = [...'convertAndSend(EX,  KEY + "x", payload)'.matchAll(CONVERT_AND_SEND)][0];
  assert.deepEqual([m[1].trim(), m[2].trim()], ["EX", 'KEY + "x"']);
  fast(() => [...("convertAndSend(a," + hostile(" ")).matchAll(CONVERT_AND_SEND)]);
});

test("worker bindings, placeholders", () => {
  assert.deepEqual([...'  - "job.a.b"\n- "job.c"'.matchAll(WORKER_BINDING)].map((m) => m[1]), ["job.a.b", "job.c"]);
  fast(() => [...hostile("\n").matchAll(WORKER_BINDING)]);
  assert.equal("job.{a}.{b}".replace(BRACE_PLACEHOLDER, "{…}"), "job.{…}.{…}");
  assert.equal("/a/{id}/b/{}".replace(PATH_VARIABLE, "x"), "/a/x/b/x");
  fast(() => hostile("{").replace(PATH_VARIABLE, "x"));
  fast(() => hostile("{").replace(BRACE_PLACEHOLDER, "x"));
});

test("javadoc", () => {
  assert.equal("/**\n * A first-class {@code Intention}.\n */".match(FIRST_JAVADOC_LINE)[1], "A first-class {@code Intention}.");
  fast(() => ("/**" + hostile(" ") + "\n *" + hostile(" ")).match(FIRST_JAVADOC_LINE));
  assert.equal("see {@link Foo bar} and {@code X}".replace(JAVADOC_INLINE_TAG, "`$1`"), "see `Foo bar` and `X`");
  fast(() => ("{@a" + hostile(" ")).replace(JAVADOC_INLINE_TAG, "x"));
  assert.equal("a\n  // c\n * d\nb".replace(COMMENT_LINE, ""), "a\n\n\nb");
  fast(() => hostile("\n").replace(COMMENT_LINE, ""));
  assert.deepEqual("@param <T> the type".match(PARAM_TAG).slice(1), ["<T>", "the type"]);
  assert.deepEqual("@param x".match(PARAM_TAG).slice(1), ["x", ""]);
  fast(() => ("@param a" + hostile(" ")).match(PARAM_TAG));
});

test("log steps", () => {
  const code = 'logStep("Phase A — Build things")\nlogStep("Workspace: up")\nlogStep("other")';
  const steps = logSteps(code);
  assert.deepEqual(steps.map((s) => [s.text, s.phase]), [["Phase A — Build things", "A"], ["Workspace: up", undefined]]);
  assert.deepEqual(splitStepText("Phase A — Build: now"), ["Phase A", "Build", "now"]);
  fast(() => logSteps('logStep("Phase a' + hostile("a")));
});

test("cli ports, property refs, trailing number", () => {
  assert.deepEqual([...'  web: intEnv("W", 3000),\n'.matchAll(CLI_PORT)].map((m) => [m[1], m[2]]), [["web", "3000"]]);
  fast(() => [...hostile("\n").matchAll(CLI_PORT)]);
  assert.deepEqual("${a.b:5000}".match(PROPERTY_REF).slice(1), ["a.b", "5000"]);
  assert.equal("${a.b}".match(PROPERTY_REF)[2], undefined);
  fast(() => ("${" + hostile("a") + "x").match(PROPERTY_REF));
  assert.equal("every 1500 (`k`)".replace(TRAILING_NUMBER, "N"), "every N (`k`)");
  assert.equal("every 90".replace(TRAILING_NUMBER, "N"), "every N");
  fast(() => (hostile("1") + "x").replace(TRAILING_NUMBER, "N"));
});

test("yaml key line", () => {
  assert.deepEqual("a: b c".match(YAML_KEY_LINE).slice(1), ["a", "b c"]);
  assert.equal("a:".match(YAML_KEY_LINE)[1], "a");
  assert.equal('"k x": v'.match(YAML_KEY_LINE)[1], '"k x"');
  assert.equal("a:b".match(YAML_KEY_LINE), null);
  fast(() => ("a:" + hostile(" ") + "\n").match(YAML_KEY_LINE));
});

test("concatenation, params, comments", () => {
  assert.deepEqual(splitConcatenation('"a" +  b+c'), ['"a"', "b", "c"]);
  assert.deepEqual(paramOf("  String   name "), { type: "String", name: "name" });
  assert.deepEqual(paramOf("Map<K, V>   m"), { type: "Map<K, V>", name: "m" });
  assert.deepEqual(paramOf("name"), { type: "name", name: "name" });
  assert.deepEqual(paramOf("List<A>"), { type: "List<A>", name: undefined });
  fast(() => paramOf("a" + hostile(" ") + hostile("b")));
  assert.equal(stripBlockComments("a/* x */b/** y */c"), "abc");
  assert.equal(stripBlockComments("a/* open"), "a/* open");
  fast(() => stripBlockComments(hostile("/*")));
});
