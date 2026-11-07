# The e2e harness — six things that cost a fifteen-minute pass each to learn

This file exists because none of what follows is visible from the code, and every line of it was
established by a run that failed in a way that took a while to read. If you are debugging the gate,
start here rather than rediscovering them.

Context: ADR-069 §0 (eight passes repairing the gate), ADR-071 (repairing the instruments).

---

## 1. httpyac's assert grammar puts the operator LAST, and a bad assertion can vanish

The dialect is `?? [js] <selector> <operator> [value]`. The **operator is the trailing token**, and
httpyac finds it by scanning, which has two consequences that are not documented anywhere:

| shape | what httpyac does |
|:---|:---|
| `?? js x === "A"` | splits on the `==` **inside** `===`, the evaluator receives `x == = "A"` → `SyntaxError` |
| `?? js ["A","B"].includes(x)` | **no assertion at all** — no tick, no cross, the line is simply absent from the output |
| `?? js …filter(…).length == 0` | works: the only `==` is where httpyac expects it |

The middle row is the dangerous one: an assertion that does not run and does not say so. Measured
against a throwaway server, not guessed.

**Variable interpolation** follows normal JS: `{{composer[0].studioKey}}` and
`{{composer.filter(s => s.inputKind == "TEXT")[0].studioKey}}` both work. `{{composer.$.[0].k}}` is
not valid JavaScript and the request errors out without ever reaching the server — which is how the
run-start contract went unexecuted from the day it was written.

**Write predicates as "count the wrong entries and compare to zero".** It keeps the only `==` in the
operator position and avoids `[` entirely.

## 2. "N requests processed" counts `###` REGIONS, not requests

`###` is httpyac's region delimiter, and this suite also uses it for banner rules and prose. The real
figures for `src/test/resources/api-contracts`:

- **121** `###` regions — the number httpyac prints and the one that used to be quoted as proof
- **66** regions that contain an actual request line
- **79** declared `?? ` assertions

That number is inflated ~1.8× and **cannot go down when a request is deleted**, because the `###`
stays. `scripts/run-api-contracts.mjs` prints all three now and fails when declared ≠ reported —
the same property as Java's `GOV-006` and the Python suite's defined-equals-collected guard.

## 3. Without `exec`, stopping a service stops its shell and orphans the JVM

Each service is launched as `/bin/sh -c "set -a && . .env && set +a && exec java -jar …"`.

**That `exec` is load-bearing.** Without it the shell forks the JVM, `process-exec:stop-all` kills the
shell it tracked, and the JVM keeps running on its port. This is not theoretical: after a
`post-integration-test` that demonstrably ran — the containers were gone — eight service JVMs were
still listening. **Teardown of the JVMs had never worked, on red runs or on green ones.**

Verify the shape with `pgrep -f 'orazaka-apps/services/.*\.jar'`: with `exec`, the tracked pid *is*
the java pid.

## 4. `infra/initdb` creates every context role with no password

`CREATE ROLE krizaka_users LOGIN;` — and nothing else. psql 15 cannot read the environment
(`\getenv` arrived in 16), ERR-125 bans a shell script in the initdb directory, and a committed
literal was audit #5. The seeds' own comments say the password is applied afterwards, from
`<CONTEXT>_DB_PASSWORD`, **by `orazaka start`**.

The harness does not run `orazaka start`. On a volume that survived from some earlier one the roles
already had passwords, so the step was invisible — until `down -v` made every run start from an empty
volume, and `identity-service` died with `FATAL: password authentication failed`.
`scripts/apply-db-role-passwords.mjs` is the harness's own path, and it **derives** the contexts from
`CREATE ROLE orazaka_<ctx> LOGIN` in the seeds rather than listing them.

⚠ The CLI's `applyRolePasswords` keeps a **hardcoded five-element list** of the same contexts. Two
implementations of one step; the hardcoded one will go stale first.

## 5. Pass a password to psql raw — `JSON.stringify` only works through a shell

`psql -v pw=<value>` with `ALTER ROLE … PASSWORD :'pw'` is right: psql does the quoting, so a
password containing a quote cannot terminate the statement, and the value never appears in `argv`.

But **how you pass it depends on whether a shell is involved**. The CLI builds a command *string*
(`execSync`), so a shell strips `JSON.stringify`'s quotation marks. `execFileSync` has no shell, so
the same expression makes the quotation marks part of the password: `ALTER ROLE` reports success,
every role "can authenticate", and the service still dies with `password authentication failed`.

A step that reports success while leaving the thing it configures broken is the exact failure this
harness exists to prevent.

## 6. `-Dit.test=` makes the gate run its suites twice, and the second pass is not independent

The `staging` profile has two failsafe executions — `e2e-tier3-playwright` (everything except
`PlaygroundMediaIT`) and `e2e-tier3-playground-media` (only it). With no filter they do not overlap.

**With `-Dit.test=…` both executions match the filter and run the same suites**, and the second
inherits the first's side effects: `JobLifecycleIT.startRunAndFindItsJob` returns `202` in the first
execution and `409` in the second, from one `mvn verify`. A red can therefore be caused purely by
re-running. Filter with care, and compare like with like when you do.

---

## Running it

```bash
./mvnw verify -P staging -pl e2e/sovkora-e2e
```

Every run resets first (`reset-stack-before-start`): leftover service JVMs are killed and
`docker compose down -v` drops the volume, so `infra/initdb` replays. That is what makes AGENTS.md
§9's word "hermetic" true rather than claimed — and it is also the only teardown that always happens,
because Maven has no `finally` and nothing survives a Ctrl-C.

Before the first request, `scripts/verify-stack-identity.mjs` compares each service's
`/actuator/info` `build.time` against what this tree packaged, and refuses a stack that is not this
build. **Its limit:** it compares *builds*, so a stale stack from the *same* build would pass — that
case is covered by the reset, not by the check.

## The voice scenarios (ADR-076)

`VoiceTurnIT` is tagged `voice` and excluded from the default gate, like `ui`: it needs the native
runtimes the hermetic stack does not start — LocalAI on `:8085` with `whisper-base` and the Piper
voices (`infra/localai/`), and Ollama with a chat model (`ORAZAKA_OLLAMA_MODEL`, measured with
`qwen2.5:7b`). Run it with the gate:

```bash
./mvnw verify -P staging -pl e2e/sovkora-e2e -De2e.voice
```

or alone, against a stack already running (`orazaka dev`), **without the reset** — the gate's
`down -v` drops the same volume `orazaka start` uses, local data included:

```bash
./mvnw -f e2e/sovkora-e2e/pom.xml test-compile failsafe:integration-test failsafe:verify \
  -Dit.test=VoiceTurnIT -Dskip.e2e=false
```

It fails, rather than skips, when a runtime is missing: a voice gate that passes without a voice
proves nothing. The audio fixture (`src/test/resources/voice/`) is synthesised, not recorded.
