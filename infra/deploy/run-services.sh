#!/usr/bin/env bash
# ==============================================================================
# Sovkora runtime — starts the JVM services of the image side by side (docs/DEPLOY_DEV.md).
# One container, several JVMs: the cheapest way to run the platform in dev. If one JVM exits, the
# others are stopped and the container exits non-zero, so App Platform restarts the whole set.
# Each JVM gets its own heap (ORAZAKA_HEAP_<NAME>, MiB) inside the container's memory; the rest is
# metaspace, code cache and thread stacks (virtual threads keep those small).
# ==============================================================================
set -euo pipefail

# A pack worker is supervised, never fatal: when it exits it is restarted after a backoff that doubles up
# to ORAZAKA_WORKER_BACKOFF_MAX seconds (reset after a run longer than that), and the JVMs keep serving.
# Only the JVMs are fatal (below): they are the platform; a worker is one capability.
supervise() {
  local name="$1"; shift
  local delay="${ORAZAKA_WORKER_BACKOFF_START:-2}" max="${ORAZAKA_WORKER_BACKOFF_MAX:-300}" started rc child="" nap=""
  # When the container is stopped the supervisor goes with its worker. Without this the container's `wait` hung on the
  # supervisor until the platform's SIGKILL and the old deployment's worker kept consuming the broker's queue next to the
  # new deployment's: a stray consumer running old pack code took half the validation jobs of a rolling deploy.
  trap '[[ -n "$child" ]] && kill -TERM "$child" 2>/dev/null; [[ -n "$nap" ]] && kill "$nap" 2>/dev/null; exit 0' TERM INT
  while true; do
    started=$(date +%s)
    "$@" &
    child=$!
    wait "$child" && rc=0 || rc=$?
    child=""
    if (( $(date +%s) - started > max )); then delay="${ORAZAKA_WORKER_BACKOFF_START:-2}"; fi
    echo "[run-services] worker $name exited (status $rc) — restarting in ${delay}s"
    sleep "$delay" &
    nap=$!
    wait "$nap" || true
    nap=""
    delay=$(( delay * 2 > max ? max : delay * 2 ))
  done
}

# Waits until something listens on host:port (bash's /dev/tcp, no curl in the image), at most `timeout` seconds: 0 when it does, 1 when not.
wait_port() {
  local host="$1" port="$2" timeout="${3:-60}" waited=0
  while (( waited < timeout )); do
    if (exec 3<>"/dev/tcp/${host}/${port}") 2>/dev/null; then return 0; fi
    sleep 1
    waited=$(( waited + 1 ))
  done
  return 1
}

# A worker started inside core receives a CLOSED list of variables, never core's whole environment (krizaka-internal#3295): core
# carries the database credentials, the identity secret and the SERVICE token, which no worker needs. A worker runs under `env -i`
# plus the names below; a variable a worker newly reads is added here and fails scripts/test/run-services.test.mjs until it is.
# Each name is one the worker's code reads (the test reads that code): no other name, and nothing sensitive but the broker password
# and the hosted-video worker's provider key.
WORKER_ENV_BASE=(PATH HOME LANG LC_ALL TZ)
# The broker (packs/worker-common/worker_broker.py, workers/media/app/broker.py); the CA file is set by each worker below.
WORKER_ENV_BROKER=(RABBITMQ_HOST RABBITMQ_PORT RABBITMQ_USER RABBITMQ_PASS RABBITMQ_SSL_ENABLED RABBITMQ_SSL_SERVER_NAME SPRING_RABBITMQ_SSL_ENABLED SPRING_RABBITMQ_SSL_SERVER_NAME SPRING_RABBITMQ_VIRTUAL_HOST SPRING_RABBITMQ_HEARTBEAT)
# document-validation: the lecture switch (lecture.py ENV_SWITCH). It calls no provider: no inference key.
WORKER_ENV_VALIDATION=(AI_LAW_LECTURE_ENABLED)
# hosted-video: the provider's URL, timeout and spend caps (remote_video.py, spend.py), its queue length, its scratch folder, its liveness files (activity.py).
# The provider key and the broker mapping are set below; the service token and the identity secret are refused on purpose
# (registration.py: without them the worker does not register with the job service and still consumes).
WORKER_ENV_HOSTED_VIDEO=(AI_INFERENCE_URL MEDIA_BROKER_EXCHANGES_PASSIVE MEDIA_REMOTE_TIMEOUT_SECONDS AI_SPEND_STATE_FILE AI_VIDEO_DAILY_RUNS AI_SPEND_DAILY_BUDGET_USD BROKER_QUEUE_MAX_LENGTH MEDIA_SCRATCH_DIR MEDIA_ACTIVITY_FILE MEDIA_HEARTBEAT_FILE)

# Fills WORKER_ENV with NAME=value for each name given that core has set; a name core does not carry is not invented.
worker_env() {
  local name
  WORKER_ENV=()
  for name in "$@"; do
    if [[ -n "${!name+x}" ]]; then WORKER_ENV+=("$name=${!name}"); fi
  done
}

# A pack worker on the validation venv: its folder under the packs, its script, its own closed list on top of the base and
# the broker's. Runs in the supervisor's own background process (`"$@" &`), so `cd` stays local and `exec` makes the worker that very
# process: the supervisor's TERM reaches the Python process itself, not a shell around it.
run_pack_worker() {
  local pack="$1" script="$2"; shift 2
  cd "${ORAZAKA_APP_DIR:-/app}/orazaka-packs/$pack/worker" || return 1
  worker_env "${WORKER_ENV_BASE[@]}" "${WORKER_ENV_BROKER[@]}" "$@"
  exec env -i "${WORKER_ENV[@]}" \
    SPRING_RABBITMQ_SSL_CA_FILE="${ORAZAKA_APP_DIR:-/app}/var/broker-ca.pem" \
    "${ORAZAKA_VALIDATION_PYTHON:-/opt/validation-venv/bin/python3}" -u "$script"
}

# The document-validation worker (job.validation.*): its closed list adds the lecture switch.
run_validation_worker() { run_pack_worker document-validation validation_worker.py "${WORKER_ENV_VALIDATION[@]}"; }

# The data-analytics worker: the figures of the data-driven-ui Studio (job.data.*), computed before the model writes and
# checked after it (krizaka-internal#3628). Stdlib arithmetic on the validation venv: no GPU, no provider, no file, and no
# variable beyond the broker's.
run_data_worker() { run_pack_worker data-analytics data_worker.py; }

# The hosted-video worker: the media worker in hosted-only mode — a CPU consumer that calls the provider's API for the models a
# provider serves (the hosted Wan 2.2), on a queue of its own (job.hostedvideo.*, which the GPU worker never matches). It needs
# no GPU and no torch (the validation venv carries its lean dependencies), so hosted video runs on dev while the GPU pod or the Mac
# is off. Its output goes to the job service next door, which seals it with the platform's key, like a GPU worker's.
run_hosted_video_worker() {
  cd "${ORAZAKA_APP_DIR:-/app}/orazaka-worker-media" || return 1
  worker_env "${WORKER_ENV_BASE[@]}" "${WORKER_ENV_BROKER[@]}" "${WORKER_ENV_HOSTED_VIDEO[@]}"
  exec env -i "${WORKER_ENV[@]}" \
  MEDIA_WORKER_HOSTED_ONLY=true MEDIA_WORKER_PORT="${ORAZAKA_HOSTED_VIDEO_PORT:-8189}" \
  MEDIA_ASSET_SINK_URL=http://localhost:8090 JOB_SERVICE_INTERNAL_URL=http://localhost:8090 \
  UPLOAD_DIR="${ORAZAKA_APP_DIR:-/app}/var/orazaka-uploads" \
  AI_INFERENCE_KEY="${AI_INFERENCE_KEY_ALL:-${AI_INFERENCE_KEY:-}}" \
  SPRING_RABBITMQ_HOST="${RABBITMQ_HOST:-}" SPRING_RABBITMQ_PORT="${RABBITMQ_PORT:-}" \
  SPRING_RABBITMQ_USERNAME="${RABBITMQ_USER:-}" SPRING_RABBITMQ_PASSWORD="${RABBITMQ_PASS:-}" \
  SPRING_RABBITMQ_SSL_CA_FILE="${ORAZAKA_APP_DIR:-/app}/var/broker-ca.pem" \
    "${ORAZAKA_VALIDATION_PYTHON:-/opt/validation-venv/bin/python3}" -u -m app.consumer
}

# The library mode of the tests (scripts/test/run-services.test.mjs): define, start nothing.
if [[ "${RUN_SERVICES_LIB:-}" == "1" ]]; then return 0 2>/dev/null || exit 0; fi


# The asset keyring (FileMasterKeyProvider): written from the MEDIA_ENCRYPTION_KEY secret, mode 0600,
# never logged. Without it the services that read or write assets refuse to start.
if [[ -n "${MEDIA_ENCRYPTION_KEY:-}" ]]; then
  mkdir -p "$HOME/.sovkora"
  umask 077
  printf '# sovkora master keyring v1\nactive = k1\nk1 = %s\n' "$MEDIA_ENCRYPTION_KEY" > "$HOME/.sovkora/master.key"
  chmod 0600 "$HOME/.sovkora/master.key"
  umask 022
fi
unset MEDIA_ENCRYPTION_KEY

# The broker's CA (TLS to the broker Droplet): Spring's SSL bundle `broker` reads this file.
if [[ -n "${RABBITMQ_CA_CERT_BASE64:-}" ]]; then
  mkdir -p "${ORAZAKA_APP_DIR:-/app}/var"
  printf '%s' "$RABBITMQ_CA_CERT_BASE64" | base64 -d > "${ORAZAKA_APP_DIR:-/app}/var/broker-ca.pem"
fi

# Shared JVM settings: small footprint first. Serial GC and C1 only (no parallel GC or C2 compiler threads), ONE compiler thread
# and two visible CPUs (the container sees the host's 8: every pool sized from it multiplied the threads of eight JVMs), a heap that
# gives back what it does not hold (free ratios 10-30 %), no perf-data mmap, bounded code cache and direct buffers.
# glibc's per-thread malloc arenas (8 x cores by default) were the biggest single overhead of eight small JVMs: two arenas.
# Measured in orazaka#499 (docker --memory=2g, the dev image's jars): 2042 MiB -> see docs/DEPLOY_DEV.md "Core memory".
export MALLOC_ARENA_MAX="${MALLOC_ARENA_MAX:-2}"
COMMON_OPTS="${ORAZAKA_JAVA_OPTS:--XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -XX:ActiveProcessorCount=2 -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=64m -Xss512k -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -XX:-UsePerfData -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom}"

# name:jar:default-heap-MiB (the ceiling, -Xmx; the live set measured at idle is 18-49 MiB), in start order (edge last: it is the public
# port). knowledge serves the recommender (ADR-078) on :8084, reached through the edge's /api/v1/recommendations route; automation serves
# the signed webhooks and schedules (ADR-084) on :8091, reached through /api/v1/automations and /hooks/.
SERVICES="${ORAZAKA_SERVICES:-users:users.jar:128 billing:billing.jar:96 job:job.jar:192 conversation:conversation.jar:224 studio:studio.jar:128 knowledge:knowledge.jar:96 automation:automation.jar:96 edge:edge.jar:80}"

# The dev chat route is a repeatable migration of the job service (db/dev, ADR-095): only this dev supervisor adds its location.
export DATABASE_JOB_MIGRATIONS_LOCATIONS="${DATABASE_JOB_MIGRATIONS_LOCATIONS:-classpath:db/migration,classpath:db/dev}"

pids=()
workers=()
stop_all() {
  for pid in "${pids[@]}" "${workers[@]}"; do kill -TERM "$pid" 2>/dev/null || true; done
  wait || true
}
trap 'stop_all; exit 143' TERM INT

for entry in $SERVICES; do
  IFS=: read -r name jar heap <<<"$entry"
  var="ORAZAKA_HEAP_${name^^}"
  heap="${!var:-$heap}"
  echo "[run-services] starting $name (heap ${heap}m)"
  # shellcheck disable=SC2086
  java $COMMON_OPTS -Xms16m -Xmx"${heap}"m -jar "${ORAZAKA_APP_DIR:-/app}/$jar" \
    > >(sed -u "s/^/[$name] /") 2>&1 &
  pids+=("$!")
  sleep "${ORAZAKA_START_STAGGER_SECONDS:-5}"
  # Studio installs its packs at startup and asks the job service which routes exist: started while the job service was still
  # booting (eight JVMs and the workers on 2 GB share the CPU), it refused the bundle and the container exited. So the next
  # services wait for the job service's port (bounded: a job service that never comes up fails the container as before).
  case "$name" in
    job) wait_port localhost 8090 "${ORAZAKA_JOB_READY_TIMEOUT:-240}" || echo "[run-services] the job service did not answer in time; continuing" ;;
  esac
done

# The Python workers of the packs, after the JVMs (the job service declares the topology they bind to). The dev broker
# is TLS only: the worker reads the same RABBITMQ_* and SPRING_RABBITMQ_SSL_ENABLED as the services, and the CA file
# written above (orazaka-packs document-validation/worker/broker.py).
for worker in ${ORAZAKA_WORKERS-validation hosted-video data}; do
  case "$worker" in
    validation)
      echo "[run-services] starting the document-validation worker (supervised)"
      supervise validation run_validation_worker > >(sed -u "s/^/[validation] /") 2>&1 &
      workers+=("$!")
      ;;
    data)
      echo "[run-services] starting the data-analytics worker (supervised)"
      supervise data run_data_worker > >(sed -u "s/^/[data] /") 2>&1 &
      workers+=("$!")
      ;;
    hosted-video)
      echo "[run-services] starting the hosted-video worker (supervised)"
      supervise hosted-video run_hosted_video_worker > >(sed -u "s/^/[hosted-video] /") 2>&1 &
      workers+=("$!")
      ;;
    *) echo "[run-services] unknown worker: $worker" >&2 ;;
  esac
done

# The first JVM to exit takes the container down with it (App Platform restarts the set).
set +e
wait -n "${pids[@]}"
status=$?
echo "[run-services] a service exited (status $status) — stopping the others"
stop_all
exit $(( status == 0 ? 1 : status ))
