# Sovkora load suite

The proof of ADR-083: k6 scenarios with thresholds tied to the SLO table (`lib/slo.js`).

```bash
brew install k6                                   # or the pinned release used by .github/workflows/load-smoke.yml

# The edge alone, with stand-ins behind it (no Docker):
node tests/load/stub-stack.mjs 9000 &
IDENTITY_INTERNAL_URL=http://127.0.0.1:9000 ROUTER_INTERNAL_URL=http://127.0.0.1:9000 \
STUDIO_INTERNAL_URL=http://127.0.0.1:9000 ABUSE_RATE_LIMIT_MODE=enforce \
  java -jar services/edge/sovkora-edge-service/target/sovkora-edge-*.jar &
k6 run -e BASE_URL=http://127.0.0.1:8088 tests/load/edge-contract.js
k6 run -e BASE_URL=http://127.0.0.1:8088 -e PROFILE=smoke -e USE_API_KEY=1 \
  -e ORAZAKA_USER_EMAIL=x -e ORAZAKA_USER_PASSWORD=x tests/load/orazaka.js

# Dev: a few requests a second, off-peak, announced in orchestration.md. Credentials come from the
# environment (secret.sh); nothing is printed.
BASE_URL=https://www.sovkora.com/edge PROFILE=dev SCENARIOS=sync,login,run NO_REPLAY=1 k6 run tests/load/orazaka.js
CHAT_BEARER=<jwt> BASE_URL=https://www.sovkora.com/edge node tests/load/chat-ttft.mjs --n 6 --gap 10

# A full local stack (docker compose): heavy. Take ~/.krizaka-worktrees/tools/heavy-lock.sh first.
BASE_URL=http://localhost:8088 PROFILE=load k6 run tests/load/orazaka.js
```

`PROFILE` = `smoke` | `dev` | `load`; `SCENARIOS` = `sync,run,login`; `RATE` and `SECONDS` narrow the sync scenario (to find a knee);
`NO_REPLAY=1` skips the idempotent replay on a stack that does not have it yet. Never point `PROFILE=load` at dev.
