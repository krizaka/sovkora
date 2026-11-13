#!/usr/bin/env bash
# ==============================================================================
# Orazaka dev — the GPU, by the hour (docs/DEPLOY_DEV.md § GPU, ADR-077).
#
#   scripts/gpu-dev.sh start big|small   create the GPU droplet: images (FLUX.2 [klein] 4B) + 9:16 video (Wan)
#   scripts/gpu-dev.sh runpod start|stop|status|count|logs   the same worker on RunPod (an H100/A100 pod by the hour, API v2, scripts/lib/gpu-runpod.sh)
#   scripts/gpu-dev.sh runpod batch prompts.txt --cap 1   N prompts → images → Bunny, $ cap, the pod terminated in a trap, then « pods: 0 running »
#   scripts/gpu-dev.sh stop              destroy it (and any RunPod pod) — always destroy, never power off (a stopped droplet bills)
#   scripts/gpu-dev.sh status            what runs, since when, what it has cost so far
#   scripts/gpu-dev.sh cost              every session of the ledger: hours × rate, and the total
#   scripts/gpu-dev.sh logs              the droplet's setup and container logs (ssh, read-only)
#   scripts/gpu-dev.sh mac               write the env file to run the Mac's worker against the same queue
#   scripts/gpu-dev.sh relay-secret      create the relay credential in the secrets file if it is missing
#   scripts/gpu-dev.sh smoke [small|mac] the sovereign smoke: start small, assert our engines through Sovkora, destroy
#   scripts/gpu-dev.sh runpod once|loop          the RunPod spend guard: stop the idle/too-old pods once, or keep watching (SOV-096)
#   scripts/gpu-dev.sh user-data big F   render the droplet's cloud-init into F (0600) and create nothing
#
#   big   = NVIDIA L40S 48 GB (or RTX 6000 Ada 48 GB), $1.57/h — Wan 2.2 TI2V 5B, 704x1280, 5 s @ 24 fps
#   small = NVIDIA RTX 4000 Ada 20 GB, $0.76/h          — Wan 2.1 T2V 1.3B, 480x832, 5 s @ 16 fps
#
# The droplet runs ghcr.io/krizaka/sovkora-media-worker:cuda-latest (sd-server + the worker), its weights on
# a Volume that outlives it (orazaka-gpu-models-<region>, 80 GB, ≈ $8/month), and dials OUT to the relay of
# dev.sovkora.com (https://dev.sovkora.com/gpu-relay): no public port, RabbitMQ never exposed. A watchdog on
# the droplet destroys it after 30 min without a job (and after GPU_MAX_HOURS whatever it does).
#
# Requires doctl (authenticated), python3, curl, ssh-keygen. Secrets come from ORAZAKA_DEV_ENV_FILE (else
# `secret.sh @dev KEY` through KRIZAKA_SECRET_TOOL) and are never printed. Each session is appended to the ledger
# (GPU_LEDGER, default ~/Documents/krizaka/orazaka-gpu-ledger.tsv) for `cost`.
# A deployment exception to [ERR-125]: it drives a cloud resource, not the local workspace.
# ==============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAME_PREFIX="orazaka-gpu"
TAG="orazaka-gpu-dev"
PROJECT="orazaka"
IMAGE="${GPU_IMAGE:-gpu-h100x1-base}"          # DigitalOcean's NVIDIA AI/ML-ready Ubuntu (drivers, Docker)
REGIONS="${GPU_REGIONS:-tor1 nyc2 atl1 ams3}"   # tor1 first (the app and the cluster are in Toronto)
WORKER_IMAGE="${GPU_WORKER_IMAGE:-ghcr.io/krizaka/sovkora-media-worker:cuda-latest}"
CHISEL_IMAGE="${GPU_CHISEL_IMAGE:-jpillora/chisel:1.12.1}"
OLLAMA_IMAGE="${GPU_OLLAMA_IMAGE:-ollama/ollama:0.40.4}"
# Chat and vision for the Studios while the GPU runs (sovereign: Apache-2.0 models on our GPU, no third party).
OLLAMA_MODELS="${GPU_OLLAMA_MODELS:-qwen2.5:7b llava:latest}"
RELAY_URL="${GPU_RELAY_URL:-https://dev.sovkora.com/gpu-relay}"
VOLUME_GB="${GPU_VOLUME_GB:-80}"
IDLE_MINUTES="${GPU_IDLE_MINUTES:-30}"
MAX_HOURS="${GPU_MAX_HOURS:-8}"
# Secrets: an explicit file (ORAZAKA_DEV_ENV_FILE, the tests), else the org's secret tool by logical source (`secret.sh @dev KEY`,
# the vault in ~/.krizaka-private since 2026-10-27). Never a path under ~/Documents, never printed.
ENV_FILE="${ORAZAKA_DEV_ENV_FILE:-}"
SECRET_TOOL="${KRIZAKA_SECRET_TOOL:-$HOME/.krizaka-worktrees/tools/secret.sh}"
SECRET_SOURCE="${GPU_SECRET_SOURCE:-@dev}"
LEDGER="${GPU_LEDGER:-$HOME/Documents/krizaka/orazaka-gpu-ledger.tsv}"
LOCAL="${GPU_LOCAL_DIR:-$HOME/.orazaka/gpu-dev}"

die() { echo "gpu-dev: $*" >&2; exit 1; }
say() { echo "gpu-dev: $*"; }

# size slugs to try, in order, and their $/hour (DigitalOcean list price, on-demand)
sizes_for() {
  case "$1" in
    big) echo "gpu-l40sx1-48gb:1.57 gpu-6000adax1-48gb:1.57" ;;
    small) echo "gpu-4000adax1-20gb:0.76" ;;
    *) die "unknown size '$1' (big or small)" ;;
  esac
}

# One value of the secrets file, into stdout for a $(…) — never echoed by this script.
secret() {
  local key="$1"
  if [[ -z "$ENV_FILE" ]]; then
    [[ -x "$SECRET_TOOL" ]] || die "no secret tool at $SECRET_TOOL (set KRIZAKA_SECRET_TOOL or ORAZAKA_DEV_ENV_FILE)"
    "$SECRET_TOOL" "$SECRET_SOURCE" "$key" 2>/dev/null
    return
  fi
  [[ -f "$ENV_FILE" ]] || die "no secrets file at $ENV_FILE"
  python3 - "$ENV_FILE" "$key" <<'PY'
import sys
path, key = sys.argv[1], sys.argv[2]
for line in open(path, encoding="utf-8"):
    line = line.strip()
    if line.startswith(key + "="):
        print(line.split("=", 1)[1].strip().strip('"'), end="")
        break
PY
}

# The dev broker's CA (krizaka/orazaka#70): from the secrets file, else the folder dev-deploy.mjs broker made it in.
broker_ca_b64() {
  local inline; inline="$(secret RABBITMQ_CA_CERT_BASE64 || true)"
  if [[ -n "$inline" ]]; then printf '%s' "$inline"; return; fi
  local file; file="$(dirname "${ENV_FILE:-$HOME/.krizaka-private/env/dev.env}")/orazaka-broker/ca.pem"
  [[ -f "$file" ]] || die "no broker CA (RABBITMQ_CA_CERT_BASE64 or $file) — node scripts/dev-deploy.mjs broker"
  base64 < "$file" | tr -d '\n'
}

droplets_json() { doctl compute droplet list --tag-name "$TAG" -o json; }

# The SERVICE token the worker presents to the job service (ADR-035), minted HERE with the identity
# secret, which never leaves this machine; it expires with the droplet's lifetime.
mint_service_token() {
  local hours="$1"
  JWT_SECRET="$(secret IDENTITY_JWT_SECRET)" python3 - "$hours" <<'PY'
import base64, hashlib, hmac, json, os, sys, time
secret = os.environ["JWT_SECRET"]
if len(secret) < 32:
    sys.exit("IDENTITY_JWT_SECRET is too short")
b64 = lambda raw: base64.urlsafe_b64encode(raw).rstrip(b"=").decode()
now = int(time.time())
header = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
claims = {"sub": "orazaka-worker-media", "roles": ["SERVICE"], "iat": now, "exp": now + int(float(sys.argv[1]) * 3600) + 1800}
payload = b64(json.dumps(claims, separators=(",", ":")).encode())
signature = b64(hmac.new(secret.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest())
print(f"{header}.{payload}.{signature}", end="")
PY
}

do_token() {
  local scoped; scoped="$(secret GPU_WATCHDOG_DO_TOKEN || true)"
  if [[ -n "$scoped" ]]; then printf '%s' "$scoped"; return; fi
  local cfg="$HOME/Library/Application Support/doctl/config.yaml"
  [[ -f "$cfg" ]] || cfg="$HOME/.config/doctl/config.yaml"
  python3 - "$cfg" <<'PY'
import re, sys
for line in open(sys.argv[1], encoding="utf-8"):
    m = re.match(r"\s*access-token:\s*(\S+)", line)
    if m:
        print(m.group(1), end="")
        break
PY
}

relay_secret() {
  [[ -n "$ENV_FILE" ]] || die "relay-secret writes to a file: set ORAZAKA_DEV_ENV_FILE to the vault's dev.env"
  local current; current="$(secret AI_GPU_RELAY_PASSWORD || true)"
  if [[ -n "$current" ]]; then say "AI_GPU_RELAY_PASSWORD already in $ENV_FILE"; return; fi
  python3 -c 'import secrets; print("AI_GPU_RELAY_PASSWORD=" + secrets.token_urlsafe(32))' >> "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  say "AI_GPU_RELAY_PASSWORD added to $ENV_FILE — apply it: node scripts/dev-deploy.mjs apply sovkora-dev"
}

ledger_add() { mkdir -p "$(dirname "$LEDGER")"; [[ -f "$LEDGER" ]] || printf 'droplet\tsize\tregion\tusd_per_hour\tcreated_at\tdestroyed_at\tprofile\n' > "$LEDGER"; printf '%s\n' "$1" >> "$LEDGER"; }

# Renders the droplet's cloud-init: the host scripts of infra/gpu, its settings and its secrets.
render_user_data() {
  local profile="$1" volume="$2" out="$3" pubkey="$4" myip="$5"
  local image_offload="false"; [[ "$profile" == "small" ]] && image_offload="true"
  RELAY_PASSWORD="$(secret AI_GPU_RELAY_PASSWORD)" \
  RABBIT_PASSWORD="$(secret RABBITMQ_PASS_GPU)" \
  BROKER_CA_B64="$(broker_ca_b64)" \
  SERVICE_TOKEN="$(mint_service_token "$MAX_HOURS")" \
  WATCHDOG_TOKEN="$(do_token)" \
  python3 - "$ROOT" "$out" "$profile" "$volume" "$WORKER_IMAGE" "$CHISEL_IMAGE" "$RELAY_URL" \
    "$IDLE_MINUTES" "$MAX_HOURS" "$image_offload" "$pubkey" "$myip" "$OLLAMA_IMAGE" "$OLLAMA_MODELS" <<'PY'
import base64, os, sys
root, out, profile, volume, worker, chisel, relay, idle, max_hours, offload, pubkey, myip, ollama, ollama_models = sys.argv[1:]
for name in ("RELAY_PASSWORD", "RABBIT_PASSWORD", "BROKER_CA_B64", "SERVICE_TOKEN", "WATCHDOG_TOKEN"):
    if not os.environ.get(name):
        sys.exit(f"gpu-dev: {name} is empty — is the secrets file complete?")
def file(path, content, mode):
    data = base64.b64encode(content.encode()).decode()
    return f"  - path: {path}\n    encoding: b64\n    permissions: '{mode}'\n    owner: root:root\n    content: {data}\n"
host_env = (f"PROFILE={profile}\nWORKER_IMAGE={worker}\nCHISEL_IMAGE={chisel}\nRELAY_URL={relay}\n"
            f"VOLUME_NAME={volume}\nIMAGE_MODEL=flux-2-klein-4b\nIMAGE_OFFLOAD={offload}\n"
            f"IDLE_MINUTES={idle}\nMAX_HOURS={max_hours}\nOLLAMA_IMAGE={ollama}\nOLLAMA_MODELS=\"{ollama_models}\"\n"
            f"SERVICES={os.environ.get('GPU_SERVICES', 'all')}\n")
worker_env = (f"SPRING_RABBITMQ_USERNAME=gpu-worker\nSPRING_RABBITMQ_PASSWORD={os.environ['RABBIT_PASSWORD']}\n"
              f"ORAZAKA_SERVICE_TOKEN={os.environ['SERVICE_TOKEN']}\n")
relay_env = f"AUTH=gpu:{os.environ['RELAY_PASSWORD']}\n"
parts = ["#cloud-config\n", "ssh_pwauth: false\n", f"ssh_authorized_keys:\n  - {pubkey}\n", "write_files:\n",
         file("/opt/orazaka-gpu/host.sh", open(f"{root}/infra/gpu/host.sh").read(), "0750"),
         file("/opt/orazaka-gpu/watchdog.sh", open(f"{root}/infra/gpu/watchdog.sh").read(), "0750"),
         file("/etc/orazaka-gpu/host.env", host_env, "0644"),
         file("/etc/orazaka-gpu/worker.env", worker_env, "0600"),
         file("/etc/orazaka-gpu/relay.env", relay_env, "0600"),
         file("/etc/orazaka-gpu/broker-ca.pem", base64.b64decode(os.environ["BROKER_CA_B64"]).decode(), "0644"),
         file("/etc/orazaka-gpu/do-token", os.environ["WATCHDOG_TOKEN"], "0600"),
         "runcmd:\n",
         "  - [sh, -c, 'ufw default deny incoming && ufw default allow outgoing && ufw allow from %s to any port 22 proto tcp && ufw --force enable']\n" % myip,
         "  - [bash, /opt/orazaka-gpu/host.sh]\n"]
fd = os.open(out, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, "w") as handle:
    handle.write("".join(parts))
PY
}

ensure_volume() {
  local region="$1" name="${NAME_PREFIX}-models-$1"
  local id; id=$(doctl compute volume list -o json | python3 -c "import json,sys; print(next((v['id'] for v in json.load(sys.stdin) or [] if v['name']=='$name' and v['region']['slug']=='$region'), ''))")
  if [[ -z "$id" ]]; then
    say "creating the models volume $name (${VOLUME_GB} GB in $region, ≈ \$$(( VOLUME_GB / 10 ))/month, kept between droplets)" >&2
    id=$(doctl compute volume create "$name" --region "$region" --size "${VOLUME_GB}GiB" --desc "Orazaka GPU model weights (scripts/gpu-dev.sh)" --format ID --no-header)
    local project_id; project_id=$(doctl projects list -o json | python3 -c "import json,sys; print(next(p['id'] for p in json.load(sys.stdin) if p['name']=='$PROJECT'))")
    doctl projects resources assign "$project_id" --resource="do:volume:$id" >/dev/null
  fi
  printf '%s %s' "$id" "$name"
}

start() {
  local profile="${1:-}"; [[ -n "$profile" ]] || die "usage: scripts/gpu-dev.sh start big|small"
  local existing; existing=$(droplets_json | python3 -c 'import json,sys; print(len(json.load(sys.stdin) or []))')
  [[ "$existing" == "0" ]] || die "a GPU droplet already runs — scripts/gpu-dev.sh status (or stop)"
  [[ -n "$(secret AI_GPU_RELAY_PASSWORD || true)" ]] || die "no relay credential — scripts/gpu-dev.sh relay-secret, then apply the app spec"

  mkdir -p "$LOCAL"; chmod 700 "$LOCAL"
  [[ -f "$LOCAL/id_ed25519" ]] || ssh-keygen -q -t ed25519 -N "" -C "orazaka-gpu-dev" -f "$LOCAL/id_ed25519"
  # The GPU image refuses root passwords: the droplet needs a DigitalOcean SSH key — this machine's own, imported once.
  local fingerprint; fingerprint=$(ssh-keygen -E md5 -lf "$LOCAL/id_ed25519.pub" | awk '{print $2}' | sed 's/^MD5://')
  doctl compute ssh-key get "$fingerprint" >/dev/null 2>&1 \
    || doctl compute ssh-key import "orazaka-gpu-dev-$(hostname -s)" --public-key-file "$LOCAL/id_ed25519.pub" >/dev/null
  local myip; myip=$(curl -fsS https://api.ipify.org)
  local userdata="$LOCAL/user-data.yaml"

  # The droplet first, where there is stock; its region's volume is created (once) and attached after —
  # so a region without stock never gets a volume billed for nothing. host.sh waits for the device.
  local id="" region="" size="" rate=""
  for candidate in $(sizes_for "$profile"); do
    size="${candidate%%:*}"; rate="${candidate##*:}"
    for region in $REGIONS; do
      render_user_data "$profile" "${NAME_PREFIX}-models-${region}" "$userdata" "$(cat "$LOCAL/id_ed25519.pub")" "$myip"
      say "trying $size in $region (\$$rate/hour while it exists)…"
      if id=$(doctl compute droplet create "${NAME_PREFIX}-${profile}" --region "$region" --size "$size" \
            --image "$IMAGE" --tag-name "$TAG" --user-data-file "$userdata" --ssh-keys "$fingerprint" \
            --enable-monitoring --wait --format ID --no-header 2>"$LOCAL/create.err"); then
        break 2
      fi
      say "  $(head -c 200 "$LOCAL/create.err")"
      id=""
    done
  done
  rm -f "$userdata"
  [[ -n "$id" ]] || die "no region had stock for the $profile size — nothing was created"

  local volume_id volume_name
  read -r volume_id volume_name <<<"$(ensure_volume "$region")"
  doctl compute volume-action attach "$volume_id" "$id" --wait >/dev/null
  say "volume $volume_name attached"

  local project_id; project_id=$(doctl projects list -o json | python3 -c "import json,sys; print(next(p['id'] for p in json.load(sys.stdin) if p['name']=='$PROJECT'))")
  doctl projects resources assign "$project_id" --resource="do:droplet:$id" >/dev/null
  local created; created=$(doctl compute droplet get "$id" -o json | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["created_at"])')
  ledger_add "$(printf '%s\t%s\t%s\t%s\t%s\t\t%s' "$id" "$size" "$region" "$rate" "$created" "$profile")"
  local ip; ip=$(doctl compute droplet get "$id" --format PublicIPv4 --no-header)
  say "droplet $id ($size, $region, $ip) — \$$rate/hour from $created"
  say "setup: volume, image pull, weights (first time ~10 min), then it dials the relay. Follow: scripts/gpu-dev.sh logs"
  say "it destroys itself after ${IDLE_MINUTES} min without a job (or ${MAX_HOURS} h); scripts/gpu-dev.sh stop ends it now"
}

stop() {
  local ids; ids=$(droplets_json | python3 -c 'import json,sys; print(" ".join(str(d["id"]) for d in json.load(sys.stdin) or []))')
  if [[ -z "$ids" ]]; then
    say "no droplet running — \$0/hour"
    [[ -z "$(secret AI_GPU_RUNPOD_API_KEY || true)" ]] || runpod_stop
    cost; return
  fi
  status
  # shellcheck disable=SC2086
  doctl compute droplet delete $ids --force
  say "destroyed $ids — billing stopped (the models volume is kept)"
  # A pod on RunPod is never left behind either.
  [[ -z "$(secret AI_GPU_RUNPOD_API_KEY || true)" ]] || runpod_stop
  cost
}

# The ledger, completed: a session without an end gets it from the droplet's destroy action (whoever
# destroyed it — stop, the watchdog), or "now" while it still runs.
cost() {
  [[ -f "$LEDGER" ]] || { say "no GPU session yet"; return; }
  mkdir -p "$LOCAL"
  curl -fsS -H "Authorization: Bearer $(do_token)" "https://api.digitalocean.com/v2/actions?per_page=200" \
    -o "$LOCAL/actions.json" 2>/dev/null || echo '{"actions": []}' > "$LOCAL/actions.json"
  droplets_json > "$LOCAL/live.json"
  python3 - "$LEDGER" "$LOCAL/actions.json" "$LOCAL/live.json" <<'PY'
import datetime as dt, json, sys
ledger = sys.argv[1]
actions = json.load(open(sys.argv[2])).get("actions", [])
live = json.load(open(sys.argv[3])) or []
now = dt.datetime.now(dt.timezone.utc)
parse = lambda s: dt.datetime.fromisoformat(s.replace("Z", "+00:00"))
destroyed = {str(a["resource_id"]): a.get("completed_at") or a.get("started_at") for a in actions
             if a.get("type") == "destroy" and a.get("resource_type") == "droplet"}
running = {str(d["id"]) for d in live}
rows = [line.rstrip("\n").split("\t") for line in open(ledger, encoding="utf-8")][1:]
out, total, changed = [], 0.0, False
for row in rows:
    row += [""] * (7 - len(row))
    droplet, size, region, rate, created, ended, profile = row
    if not ended and droplet in destroyed and destroyed[droplet]:
        ended, changed = destroyed[droplet], True
        row[5] = ended
    end = parse(ended) if ended else (now if droplet in running else None)
    hours = (end - parse(created)).total_seconds() / 3600 if end else 0.0
    usd = hours * float(rate)
    total += usd
    state = "running" if droplet in running else ("destroyed" if ended else "gone (end unknown)")
    print(f"  {droplet:>11}  {profile:5} {size:22} {region}  {created[:16]} → {(ended or 'now')[:16]:16}  {hours:5.2f} h × ${float(rate):.2f} = ${usd:6.2f}  {state}")
    out.append(row)
print(f"  total GPU: ${total:.2f}")
if changed:
    with open(ledger, "w", encoding="utf-8") as handle:
        handle.write("droplet\tsize\tregion\tusd_per_hour\tcreated_at\tdestroyed_at\tprofile\n")
        handle.writelines("\t".join(r) + "\n" for r in out)
PY
}

status() {
  droplets_json | python3 -c "
import json, sys, datetime as dt
ds = json.load(sys.stdin) or []
if not ds: print('gpu-dev: off — \$0/hour')
for d in ds:
    created = dt.datetime.fromisoformat(d['created_at'].replace('Z', '+00:00'))
    hours = (dt.datetime.now(dt.timezone.utc) - created).total_seconds() / 3600
    ip = next((n['ip_address'] for n in d['networks']['v4'] if n['type'] == 'public'), '?')
    price = d['size'].get('price_hourly', 0)
    print(f\"gpu-dev: {d['id']} {d['name']} {d['size_slug']} {d['region']['slug']} {d['status']} at {ip}, up {hours:.2f} h ≈ \${hours * price:.2f} (\${price}/hour)\")
"
  doctl compute volume list --format Name,Size,Region,DropletIDs 2>/dev/null | grep -E "Name|${NAME_PREFIX}-models" || true
}

logs() {
  local ip; ip=$(droplets_json | python3 -c 'import json,sys; ds=json.load(sys.stdin) or []; print(next((n["ip_address"] for d in ds for n in d["networks"]["v4"] if n["type"]=="public"), ""))')
  [[ -n "$ip" ]] || die "no GPU droplet running"
  ssh -i "$LOCAL/id_ed25519" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile="$LOCAL/known_hosts" "root@$ip" \
    'journalctl -t orazaka-gpu -t orazaka-gpu-watchdog --no-pager -n 60; for c in sd-server worker relay; do echo "== $c"; docker logs --tail 25 $c 2>&1; done; nvidia-smi --query-gpu=name,utilization.gpu,memory.used,memory.total --format=csv'
}

# The sovereign smoke (scripts/gpu-smoke.mjs): our engines, through Sovkora's routing, asserted — then destroyed.
wait_ready() {
  local ip="$1" deadline=$(( $(date +%s) + ${GPU_READY_MINUTES:-75} * 60 ))
  while (( $(date +%s) < deadline )); do
    if ssh -i "$LOCAL/id_ed25519" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile="$LOCAL/known_hosts" \
         -o ConnectTimeout=10 -o BatchMode=yes "root@$ip" test -e /var/lib/orazaka-gpu/ready 2>/dev/null; then
      say "the GPU host is serving"; return 0
    fi
    sleep 30
  done
  die "the GPU host was not ready after ${GPU_READY_MINUTES:-75} min"
}

# Destroys every GPU droplet of the tag, whatever state this script is in (the smoke's trap).
destroy_all() {
  local ids; ids=$(droplets_json | python3 -c 'import json,sys; print(" ".join(str(d["id"]) for d in json.load(sys.stdin) or []))')
  # shellcheck disable=SC2086
  [[ -z "$ids" ]] || { say "destroying $ids"; doctl compute droplet delete $ids --force; }
}

smoke() {
  local host="${1:-small}" out="${GPU_SMOKE_OUT:-$HOME/Documents/krizaka/orazaka-smoke/last.json}"
  if [[ "$host" == "mac" ]]; then
    node "$ROOT/scripts/gpu-smoke.mjs" --host mac --out "$out"
    return
  fi
  [[ "$host" == "small" || "$host" == "big" ]] || die "usage: scripts/gpu-dev.sh smoke [small|big|mac]"
  # Whatever happens from here — a failed check, Ctrl+C, a crash — the droplet is destroyed.
  trap destroy_all EXIT INT TERM
  start "$host"
  local ip; ip=$(droplets_json | python3 -c 'import json,sys; ds=json.load(sys.stdin) or []; print(next((n["ip_address"] for d in ds for n in d["networks"]["v4"] if n["type"]=="public"), ""))')
  wait_ready "$ip"
  local status=0
  node "$ROOT/scripts/gpu-smoke.mjs" --host "droplet-$host" --out "$out" || status=$?
  destroy_all
  trap - EXIT INT TERM
  cost | tail -2
  return "$status"
}

# The Mac's Metal worker on the same queue, for free (docs/DEPLOY_DEV.md § The Mac as the GPU).
mac() {
  mkdir -p "$LOCAL"; chmod 700 "$LOCAL"
  local out="$LOCAL/mac-worker.env"
  RELAY_PASSWORD="$(secret AI_GPU_RELAY_PASSWORD)" RABBIT_PASSWORD="$(secret RABBITMQ_PASS_GPU)" \
  INFERENCE_KEY="$(secret AI_INFERENCE_KEY_ALL || secret AI_INFERENCE_KEY)" \
  BROKER_CA_B64="$(broker_ca_b64)" \
  SERVICE_TOKEN="$(mint_service_token 12)" python3 - "$out" "$LOCAL/broker-ca.pem" <<'PY'
import base64, os, sys
with open(sys.argv[2], "w") as ca:
    ca.write(base64.b64decode(os.environ["BROKER_CA_B64"]).decode())
fd = os.open(sys.argv[1], os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, "w") as handle:
    handle.write(
        f"AUTH=gpu:{os.environ['RELAY_PASSWORD']}\n"
        "SPRING_RABBITMQ_HOST=127.0.0.1\nSPRING_RABBITMQ_PORT=15671\nSPRING_RABBITMQ_USERNAME=gpu-worker\n"
        f"SPRING_RABBITMQ_SSL_ENABLED=true\nSPRING_RABBITMQ_SSL_CA_FILE={sys.argv[2]}\nSPRING_RABBITMQ_SSL_SERVER_NAME=broker-dev.orazaka.com\n"
        f"SPRING_RABBITMQ_PASSWORD={os.environ['RABBIT_PASSWORD']}\n"
        f"ORAZAKA_SERVICE_TOKEN={os.environ['SERVICE_TOKEN']}\n"
        "MEDIA_ASSET_SINK_URL=http://127.0.0.1:18090\nJOB_SERVICE_INTERNAL_URL=http://127.0.0.1:18090\n"
    )
    if os.environ.get("INFERENCE_KEY"):
        # The provider key of the models routed rather than run here (wan2-2-t2v-a14b); in the 0600 file, never printed.
        handle.write(f"AI_INFERENCE_KEY={os.environ['INFERENCE_KEY']}\n")
PY
  say "wrote $out (0600, valid 12 h). Then, in two terminals:"
  # The same remotes as a droplet: the broker and the job service in, and the Mac's own engines back into the app
  # (LocalAI :8085 — Whisper and Piper —, sd-server :8086, the worker's pipeline :8188, Ollama :11434) — whichever of them runs on the Mac answers.
  echo "  set -a; . $out; set +a; chisel client --keepalive 25s $RELAY_URL 127.0.0.1:15671:broker-dev.orazaka.com:5671 127.0.0.1:18090:core:8090 R:0.0.0.0:8085:127.0.0.1:8085 R:0.0.0.0:8086:127.0.0.1:8086 R:0.0.0.0:8188:127.0.0.1:8188 R:0.0.0.0:11434:127.0.0.1:11434"
  echo "  set -a; . $out; set +a; cd workers/media && .venv/bin/python -u -m app.consumer"
}

# RunPod: the same worker on a pod by the hour (scripts/lib/gpu-runpod.sh).
# shellcheck source=lib/gpu-runpod.sh
source "$ROOT/scripts/lib/gpu-runpod.sh"

case "${1:-}" in
  runpod) shift; runpod "$@" ;;
  start) start "${2:-}" ;;
  stop) stop ;;
  status) status ;;
  cost) cost ;;
  logs) logs ;;
  mac) mac ;;
  relay-secret) relay_secret ;;
  smoke) smoke "${2:-small}" ;;
  # Renders a droplet's cloud-init into a 0600 file and creates nothing (tests, review).
  user-data) render_user_data "${2:?big|small}" "${NAME_PREFIX}-models-${REGIONS%% *}" "${3:?output file}" "${GPU_SSH_PUBKEY:-ssh-ed25519 AAAA test}" "${GPU_SSH_FROM:-127.0.0.1}" ;;
  *) echo "usage: scripts/gpu-dev.sh start big|small | stop | status | cost | logs | mac | relay-secret | smoke [small|mac]" >&2; exit 2 ;;
esac
