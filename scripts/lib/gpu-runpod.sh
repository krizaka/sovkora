# shellcheck shell=bash
# ==============================================================================
# scripts/gpu-dev.sh runpod … — the same GPU worker on RunPod (a pod by the hour), sourced by gpu-dev.sh.
# On RunPod API v2 (orazaka#508: v1 REST and GraphQL stop on 2026-11-15); every call goes through scripts/runpod-guard.py.
#
#   gpu-dev.sh runpod start [gpu]   create the pod (H100 80 GB by default; GPU_RUNPOD_GPUS overrides the priority list):
#                                    a network volume keeps the weights between pods, the worker is built in the pod from its
#                                    public repository (GHCR is private), the tunnel dials OUT to the relay of dev.sovkora.com
#   gpu-dev.sh runpod stop          TERMINATE it — a pod is never left running or paused; the ledger gets its end
#   gpu-dev.sh runpod status        what runs, since when, what it has cost so far
#   gpu-dev.sh runpod count         « pods: N running » (exit 3 when N > 0)
#   gpu-dev.sh runpod logs          the pod's boot and worker logs (ssh, read-only)
#   gpu-dev.sh runpod batch FILE [--cap USD] [--model M] [--size WxH] [--refs refs.json] [--batch NAME] [--gpu G] [--out DIR]
#                                    the unattended render batch: a pod, N prompts (one per line) → images → Bunny, the dollar
#                                    cap enforced, the pod terminated in a trap whatever happens, then « pods: 0 running »
#
# The API key is AI_GPU_RUNPOD_API_KEY, read like every secret by gpu-dev.sh secret(): never printed. Sessions go to the same
# ledger as the droplets (id "rp-<pod>"), so `gpu-dev.sh cost` adds them up. A budget guard refuses to start when the
# account balance is below GPU_RUNPOD_MIN_BALANCE (default 10 USD).
# Terminate, not pause: for occasional runs a stopped pod still bills its container disk, and the weights live on the network volume.
# ==============================================================================
RP_IMAGE="${GPU_RUNPOD_IMAGE:-runpod/pytorch:2.8.0-py3.11-cuda12.8.1-cudnn-devel-ubuntu22.04}"
RP_VOLUME="${GPU_RUNPOD_VOLUME:-orazaka-gpu-models}"
RP_VOLUME_GB="${GPU_RUNPOD_VOLUME_GB:-200}"
# Priority list: the 80 GB cards (H100, then A100), then the 48 GB L40S (5B only).
RP_GPUS="${GPU_RUNPOD_GPUS:-NVIDIA H100 80GB HBM3|NVIDIA H100 PCIe|NVIDIA A100-SXM4-80GB|NVIDIA A100 80GB PCIe|NVIDIA L40S}"
# Datacentres to try, nearest to Toronto (the app and the relay) first; the volume lives in one of them.
RP_DCS="${GPU_RUNPOD_DCS:-CA-MTL-1 CA-MTL-3 US-GA-2 US-MO-1 US-NE-1 US-KS-2 EU-NL-1}"
RP_POD="" RP_RATE=""

rp_key() { secret AI_GPU_RUNPOD_API_KEY; }

# The v2 client: rp_py <command> [args] (scripts/runpod-guard.py --help). The key travels in the environment of the call only.
rp_py() { AI_GPU_RUNPOD_API_KEY="$(rp_key)" python3 "$ROOT/scripts/runpod-guard.py" "$@"; }

rp_balance() { rp_py balance; }

# « id status rate gpu name » of the orazaka pods that bill (not EXITED / TERMINATED).
rp_live() { rp_py list orazaka-gpu 2>/dev/null | awk '$2 != "EXITED" && $2 != "TERMINATED"' || true; }

# KEY=value lines of the pod's output zone (a driver may define its own before sourcing this file: the tests do).
declare -F gpu_out_env >/dev/null || gpu_out_env() { local pod="$1"; node "$ROOT/scripts/gpu-out/cli.mjs" pod-env "$pod"; }

runpod_start() {
  [[ -n "$(secret AI_GPU_RUNPOD_API_KEY || true)" ]] || die "no AI_GPU_RUNPOD_API_KEY in ${ENV_FILE:-$SECRET_SOURCE}"
  # MEASURE=1 (gpu-dev.sh runpod measure): a pod that measures and ends, with no relay, no broker, no service token.
  [[ -n "${MEASURE:-}" || -n "$(secret AI_GPU_RELAY_PASSWORD || true)" ]] || die "no relay credential — scripts/gpu-dev.sh relay-secret"
  local running; running=$(rp_live | wc -l | tr -d ' ')
  [[ "$running" == "0" ]] || die "a RunPod pod already runs — scripts/gpu-dev.sh runpod status (or stop)"
  local balance spend; read -r balance spend <<<"$(rp_balance)" || die "RunPod balance unreadable (the account balance has no v2 endpoint yet) — nothing was created"
  python3 -c "import sys; sys.exit(0 if float('$balance') >= float('${GPU_RUNPOD_MIN_BALANCE:-10}') else 1)" \
    || die "RunPod balance is \$$balance, below the guard (GPU_RUNPOD_MIN_BALANCE)"
  say "RunPod balance \$$balance, spending \$$spend/hour now"

  local max_hours="${GPU_MAX_HOURS:-8}" worker_ref
  # GPU_WORKER_REF runs a worker commit not yet on main (a branch under review), else main's head.
  worker_ref="${GPU_WORKER_REF:-$(git ls-remote https://github.com/krizaka/sovkora.git refs/heads/main | cut -c1-40)}"
  mkdir -p "$LOCAL"; chmod 700 "$LOCAL"
  [[ -f "$LOCAL/id_ed25519" ]] || ssh-keygen -q -t ed25519 -N "" -C "orazaka-gpu-dev" -f "$LOCAL/id_ed25519"
  local body="$LOCAL/runpod-body.json"
  local dc gpu_list; gpu_list="$RP_GPUS"
  [[ -z "${1:-}" ]] || gpu_list="$1"
  for dc in $RP_DCS; do
    local volume=""
    # GPU_RUNPOD_VOLUME=none: no network volume (a monthly cost); the weights are downloaded to the container disk at each start.
    [[ "$RP_VOLUME" == "none" ]] || volume=$(rp_py volume-id "$RP_VOLUME" "$dc")
    if [[ -z "$volume" && "$RP_VOLUME" != "none" ]]; then
      # A volume pins its pods to one datacentre: it is created only when asked (GPU_RUNPOD_CREATE_VOLUME=1), never as a side effect.
      [[ -n "${GPU_RUNPOD_CREATE_VOLUME:-}" ]] || { say "  $dc: no models volume"; continue; }
      say "creating the models volume $RP_VOLUME (${RP_VOLUME_GB} GB in $dc, kept between pods)"
      volume=$(rp_py volume-create "$RP_VOLUME" "$dc" "$RP_VOLUME_GB") || continue
    fi
    local gpu gpus
    # The pod's own output zone (krizaka-internal#2121): URL under <day>/<run>, header name and THAT zone's key, from infra/gpu/gpu-out.settings.json.
    # Never a site's key; read here, never printed. A measurement pod writes nothing, so it gets none.
    if [[ -z "${MEASURE:-}" ]]; then
      local gk gv
      while IFS='=' read -r gk gv; do [[ -n "$gk" ]] && export "$gk=$gv"; done < <(gpu_out_env "run-$(date -u +%H%M%S)")
      [[ -n "${GPU_OUT_URL:-}" ]] || die "the GPU output zone gave no URL: nothing was created"
    fi
    # Before paying: do the weights fit, and what does fetching them cost? (GPU_DOWNLOAD_GB = the model's `downloadGb` in models.yaml)
    if [[ -n "${GPU_DOWNLOAD_GB:-}" ]]; then
      node "$ROOT/scripts/gpu-boot-preflight.mjs" --download-gb "$GPU_DOWNLOAD_GB" --disk-gb "${GPU_RUNPOD_DISK_GB:-60}" \
        --volume "${GPU_RUNPOD_VOLUME:-}" --rate "${GPU_PREFLIGHT_RATE:-5.5}" || die "the weights do not fit: nothing was created"
    fi
    # v2 takes one GPU type per create: the priority list is walked here.
    IFS='|' read -ra gpus <<<"$gpu_list"
    for gpu in "${gpus[@]}"; do
      # Every credential reaches the pod as an environment variable (RunPod's own, visible to this account only).
      WORKER_REF="$worker_ref" PROFILE="${GPU_RUNPOD_PROFILE:-big}" DC="$dc" VOLUME="$volume" MAX_HOURS="$max_hours" \
      IDLE_MINUTES="${GPU_IDLE_MINUTES:-30}" RP_IMAGE="$RP_IMAGE" PUBKEY="$(cat "$LOCAL/id_ed25519.pub")" \
      RELAY_URL="$RELAY_URL" RELAY_AUTH="${MEASURE:+x}${MEASURE:-gpu:$(secret AI_GPU_RELAY_PASSWORD)}" \
      RABBIT_PASSWORD="${MEASURE:+x}${MEASURE:-${STILLS:+x}}${MEASURE:-${STILLS:-$(secret RABBITMQ_PASS_GPU)}}" \
      SERVICE_TOKEN="${MEASURE:+x}${MEASURE:-${STILLS:+x}}${MEASURE:-${STILLS:-$(mint_service_token "$max_hours")}}" \
      BROKER_CA_B64="${MEASURE:+x}${MEASURE:-${STILLS:+x}}${MEASURE:-${STILLS:-$(broker_ca_b64)}}" \
      STILLS="${STILLS:-}" MEASURE="${MEASURE:-}" MEASURE_ARGS="${MEASURE_ARGS:-}" MEASURE_PIP="${MEASURE_PIP:-}" \
      BOOT="$ROOT/infra/gpu/$([[ -n "${MEASURE:-}" ]] && echo runpod-measure.sh || echo runpod-bootstrap.sh)" \
      HF_TOKEN="$(secret AI_HUGGINGFACE_TOKEN 2>/dev/null || true)" DISK_GB="${GPU_RUNPOD_DISK_GB:-}" \
      rp_py body "$body" "$gpu"
      say "trying $dc ($gpu)…"
      local pod
      if pod=$(rp_py create "$body"); then
        rm -f "$body"
        local pid rate gname; read -r pid rate gname <<<"$pod"
        RP_POD="$pid" RP_RATE="$rate"
        ledger_add "$(printf 'rp-%s\t%s\t%s\t%s\t%s\t\t%s' "$pid" "$gname" "$dc" "$rate" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" runpod)"
        say "pod $pid ($gname, $dc) — \$$rate/hour; it terminates itself after ${GPU_IDLE_MINUTES:-30} min without a job (or $max_hours h); gpu-dev.sh runpod stop ends it now"
        return 0
      fi
    done
  done
  rm -f "$body"
  die "no RunPod datacentre had the GPU in stock (or a models volume) — nothing was created"
}

runpod_stop() {
  local id
  while read -r id _; do
    [[ -n "$id" ]] || continue
    rp_py terminate "$id" && say "terminated RunPod pod $id — billing stopped (the models volume is kept)"
  done < <(rp_live)
  if [[ -z "$(rp_live)" ]]; then say "no RunPod pod running — \$0/hour"; fi
  runpod_ledger_close
}

# Closes the ledger rows of pods that no longer exist (ended here, or by the pod's own watchdog), "now" as their end.
runpod_ledger_close() {
  [[ -f "$LEDGER" ]] || return 0
  local live; live=$(rp_py list 2>/dev/null | awk '{print "rp-"$1}' | tr '\n' ' ')
  LIVE="$live" python3 - "$LEDGER" <<'PY'
import datetime as dt, os, sys
path = sys.argv[1]; live = set(os.environ.get("LIVE", "").split())
now = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
rows = [l.rstrip("\n").split("\t") for l in open(path, encoding="utf-8")]
out = [rows[0]]
for r in rows[1:]:
    r += [""] * (7 - len(r))
    if r[0].startswith("rp-") and not r[5] and r[0] not in live:
        r[5] = now
    out.append(r)
open(path, "w", encoding="utf-8").write("".join("\t".join(r) + "\n" for r in out))
PY
}

runpod_status() {
  local id st rate gpu name
  local any=""
  while read -r id st rate gpu name; do
    [[ -n "$id" ]] || continue
    any=1
    echo "gpu-dev: RunPod $id $name $gpu $st (\$$rate/hour)"
  done < <(rp_py list orazaka-gpu)
  [[ -n "$any" ]] || echo 'gpu-dev: RunPod off — $0/hour'
  local balance spend; read -r balance spend <<<"$(rp_balance)" && say "RunPod balance \$$balance, spending \$$spend/hour"
}

runpod_count() { rp_py count; }

runpod_ssh_target() {
  local id; id=$(rp_live | awk 'NR==1{print $1}'); [[ -n "$id" ]] || return 1
  rp_py ssh "$id"
}

runpod_logs() {
  local ip port; read -r ip port <<<"$(runpod_ssh_target)" || die "no RunPod pod running"
  [[ -n "$ip" && -n "$port" ]] || die "the pod has no public ssh yet"
  ssh -i "$LOCAL/id_ed25519" -p "$port" -o ConnectTimeout=15 -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile="$LOCAL/known_hosts" "root@$ip" \
    'tail -n '"${GPU_LOG_LINES:-25}"' /var/log/orazaka-boot.log; for f in orazaka-worker orazaka-sd-server orazaka-relay orazaka-watchdog; do echo "== $f"; tail -n 20 /var/log/$f.log 2>&1; done; nvidia-smi --query-gpu=name,utilization.gpu,memory.used,memory.total --format=csv'
}

# The pod is ended whatever happens (success, error, Ctrl-C, kill): the trap of `runpod batch`. It then proves it: « pods: 0 running ».
runpod_batch_cleanup() {
  local status=$?
  trap - EXIT INT TERM HUP
  say "batch: ending the pod"
  # The boot log first (krizaka-internal#138: a pod ended without it left « engine could not be reached » unexplained).
  if [[ -n "${BATCH_OUT:-}" && -n "$(rp_live)" ]]; then
    mkdir -p "$BATCH_OUT"
    ( GPU_LOG_LINES=200 runpod_logs ) > "$BATCH_OUT/pod-boot.log" 2>&1 && say "batch: the pod's boot log is in $BATCH_OUT/pod-boot.log" || true
  fi
  local try
  for try in 1 2 3; do
    runpod_stop && [[ -z "$(rp_live)" ]] && break
    sleep 5
  done
  runpod_count || say "WARNING: a pod still bills — scripts/gpu-dev.sh runpod stop"
  exit "$status"
}

runpod_batch() {
  local prompts="${1:-}"; shift || true
  [[ -f "$prompts" ]] || die "usage: runpod batch PROMPTS.txt [--cap USD] [--model M] [--size WxH] [--refs refs.json] [--batch NAME] [--gpu G] [--out DIR]"
  local cap="${GPU_BATCH_CAP:-1}" model="" size="" batch="" gpu="" out="" refs=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --cap) cap="$2" ;; --model) model="$2" ;; --size) size="$2" ;; --batch) batch="$2" ;; --gpu) gpu="$2" ;; --out) out="$2" ;; --refs) refs="$2" ;;
      *) die "runpod batch: unknown option $1" ;;
    esac
    shift 2
  done
  python3 -c "import sys; sys.exit(0 if float('$cap') > 0 else 1)" || die "runpod batch: --cap must be a positive dollar amount"
  out="${out:-$LOCAL/batch-$(date -u +%Y%m%dT%H%M%SZ)}"; BATCH_OUT="$out"
  # Free preflight: the prompts and their reference names are read BEFORE a pod is created (an unknown name must cost nothing).
  node "$ROOT/scripts/runpod-batch.mjs" --prompts "$prompts" --validate-only ${refs:+--refs "$refs"} || die "runpod batch: the prompts file is not valid"
  trap runpod_batch_cleanup EXIT INT TERM HUP
  # FREE preflight (orazaka#508): the Studio is asked the exact run BEFORE any GPU is rented; a refusal a booted pod cannot fix stops here.
  if [[ -z "${GPU_BATCH_SKIP_PREFLIGHT:-}" ]]; then
    node "$ROOT/scripts/studio-preflight.mjs" ${model:+--model "$model"} ${model:+--worker-ref "${GPU_WORKER_REF:-$(git ls-remote https://github.com/krizaka/sovkora.git refs/heads/main | cut -c1-40)}"} ${size:+--size "$size"} || die "preflight refused: no GPU was rented"
  fi
  # The pod's own watchdog gets the cap too: at the dearest rate we would ever start (GPU_BATCH_MAX_RATE), the hours the cap pays for.
  # STILLS=1: the pod serves the image engine only, up in minutes (no video weights, no upscaler, no consumer).
  STILLS=1 GPU_MAX_HOURS="$(python3 -c "print(max(0.25, round(float('$cap') / float('${GPU_BATCH_MAX_RATE:-4}'), 2)))")" runpod_start ${gpu:+"$gpu"}
  python3 -c "import sys; sys.exit(0 if float('$RP_RATE') <= float('$cap') else 1)" || die "the pod costs \$$RP_RATE/hour: more than the whole cap (\$$cap)"
  local started; started=$(date -u +%s)
  say "batch: $(grep -cv '^\s*\(#\|$\)' "$prompts") prompts, cap \$$cap, results in $out"
  node "$ROOT/scripts/runpod-batch.mjs" --prompts "$prompts" --out "$out" --rate "$RP_RATE" --started "$started" --cap "$cap" \
    ${model:+--model "$model"} ${size:+--size "$size"} ${batch:+--batch "$batch"} ${refs:+--refs "$refs"} ${GPU_BATCH_NO_UPLOAD:+--no-upload}
}

runpod() {
  case "${1:-}" in
    start) shift; runpod_start "$@" ;;
    # A pod that only measures (infra/gpu/runpod-measure.sh): MEASURE_ARGS="load wan2.2-ti2v-5b" … ; it ends itself.
    measure) shift; MEASURE=1 runpod_start "$@" ;;
    stop) runpod_stop ;;
    status) runpod_status ;;
    count) runpod_count ;;
    logs) runpod_logs ;;
    batch) shift; runpod_batch "$@" ;;
    # The spend guard (SOV-096): ends a pod idle GPU_IDLE_MINUTES (20) or older than GPU_MAX_HOURS (6), once or in a loop.
    once|loop)
      AI_GPU_RUNPOD_API_KEY="${AI_GPU_RUNPOD_API_KEY:-$(rp_key)}" python3 "$ROOT/scripts/runpod-guard.py" "$1" "${@:2}" ;;
    *) echo "usage: scripts/gpu-dev.sh runpod start [gpu] | measure [gpu] | stop | status | count | logs | batch FILE | once | loop" >&2; exit 2 ;;
  esac
}
