#!/usr/bin/env bash
# ==============================================================================
# The GPU droplet's own setup (docs/DEPLOY_DEV.md § GPU) — run once by cloud-init as root.
#
#   1. mounts the models volume (/models) that outlives every droplet; formats it the first time;
#   2. closes the metadata service to containers (it holds this droplet's user-data);
#   3. checks Docker reaches the GPU (installs the NVIDIA container toolkit if the image lacks it);
#   4. fetches the profile's weights onto the volume, once (python -m app.fetch_models);
#   5. starts, on a private bridge network: sd-server (images), the media worker (video), Ollama (chat,
#      vision) and the chisel client that dials OUT to the relay of dev.sovkora.com — the droplet opens no
#      port; the broker (TLS, private CA) is reached through it and verified end to end;
#   0. first of all, arms the watchdog (every minute): 30 min without a job, or the hard lifetime, and it destroys
#      the droplet through the API. A stopped droplet is still billed; this one never stops, it ends.
#
# Configuration: /etc/orazaka-gpu/host.env (settings), worker.env and relay.env (secrets, 0600),
# do-token (0600, root, the watchdog's only). Logs: journalctl -t orazaka-gpu, docker logs <name>.
# ==============================================================================
set -euo pipefail
exec > >(logger -t orazaka-gpu -s 2>/dev/console) 2>&1

CONF=/etc/orazaka-gpu
STATE=/var/lib/orazaka-gpu
# shellcheck disable=SC1091
source "$CONF/host.env"   # PROFILE, WORKER_IMAGE, RELAY_URL, VOLUME_NAME, IMAGE_MODEL, IMAGE_OFFLOAD, CHISEL_IMAGE, OLLAMA_*
mkdir -p "$STATE/state" "$STATE/uploads"

step() { echo "orazaka-gpu: $*"; }

# --- 0. the watchdog, first: whatever fails below, this droplet still ends ---------------------------
cat > /etc/systemd/system/orazaka-gpu-watchdog.service <<UNIT
[Unit]
Description=Destroy this GPU droplet when it has been idle (Orazaka dev)
[Service]
Type=oneshot
ExecStart=/opt/orazaka-gpu/watchdog.sh
UNIT
cat > /etc/systemd/system/orazaka-gpu-watchdog.timer <<UNIT
[Unit]
Description=Orazaka GPU idle watchdog, every minute
[Timer]
OnBootSec=2min
OnUnitActiveSec=1min
AccuracySec=10s
[Install]
WantedBy=timers.target
UNIT
systemctl daemon-reload
systemctl enable --now orazaka-gpu-watchdog.timer
touch "$STATE/state/last-activity"
step "watchdog armed (idle ${IDLE_MINUTES:-30} min, lifetime ${MAX_HOURS:-8} h)"


# --- 1. the models volume --------------------------------------------------------------------------
device="/dev/disk/by-id/scsi-0DO_Volume_${VOLUME_NAME}"
for _ in $(seq 1 120); do [[ -e "$device" ]] && break; sleep 5; done
[[ -e "$device" ]] || { step "volume $VOLUME_NAME never appeared"; exit 1; }
if ! blkid "$device" >/dev/null 2>&1; then
  step "formatting $VOLUME_NAME (first use)"
  mkfs.ext4 -q -L orazaka-models "$device"
fi
mkdir -p /models
mountpoint -q /models || mount -o discard,defaults,noatime "$device" /models
chown 10001:10001 /models "$STATE/state" "$STATE/uploads" "$STATE/state/last-activity"
step "models volume mounted ($(df -h /models | awk 'NR==2 {print $3 " used of " $2}'))"

# --- 2. the metadata service is the host's, not the containers' -------------------------------------
iptables -C DOCKER-USER -d 169.254.169.254 -j DROP 2>/dev/null || iptables -I DOCKER-USER -d 169.254.169.254 -j DROP

# --- 3. the worker image, then Docker sees the GPU ------------------------------------------------
# GHCR first (CI publishes it); while the package is not public, the image built from the worker's own
# public repository at its main commit, kept on the volume so it is built once per commit, not per start.
worker_image() {
  if docker pull -q "$WORKER_IMAGE" >/dev/null 2>&1; then step "image pulled: $WORKER_IMAGE"; return; fi
  local sha; sha=$(git ls-remote https://github.com/krizaka/orazaka-worker-media.git refs/heads/main | cut -c1-12)
  local tar="/models/images/orazaka-worker-media-${sha}.tar"
  mkdir -p /models/images
  if [[ -f "$tar" ]]; then
    docker load -q -i "$tar" >/dev/null && docker tag "orazaka-worker-media:${sha}" "$WORKER_IMAGE"
    step "image loaded from the volume (worker-media ${sha})"; return
  fi
  step "building the worker image from krizaka/orazaka-worker-media@${sha} (once per commit, ~20 min)"
  local src; src=$(mktemp -d)
  git clone -q --depth 1 https://github.com/krizaka/orazaka-worker-media.git "$src"
  # The DigitalOcean GPU image ships Docker without buildx, and Dockerfile.cuda needs BuildKit.
  docker buildx version >/dev/null 2>&1 || { apt-get update -qq && apt-get install -y -qq docker-buildx >/dev/null; }
  DOCKER_BUILDKIT=1 docker build -q -f "$src/Dockerfile.cuda" -t "orazaka-worker-media:${sha}" "$src" >/dev/null
  rm -rf "$src"
  docker tag "orazaka-worker-media:${sha}" "$WORKER_IMAGE"
  rm -f /models/images/orazaka-worker-media-*.tar
  docker save -o "$tar.part" "orazaka-worker-media:${sha}" && mv "$tar.part" "$tar"
  step "image built and kept on the volume ($(du -h "$tar" | cut -f1))"
}
worker_image

if ! docker run --rm --gpus all "$WORKER_IMAGE" nvidia-smi -L 2>/dev/null; then
  if ! docker run --rm --gpus all "$WORKER_IMAGE" nvidia-smi -L; then
    step "installing the NVIDIA container toolkit"
    curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg
    curl -fsSL https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list \
      | sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' \
      > /etc/apt/sources.list.d/nvidia-container-toolkit.list
    apt-get update -q && apt-get install -y -q nvidia-container-toolkit
    nvidia-ctk runtime configure --runtime=docker && systemctl restart docker
    iptables -C DOCKER-USER -d 169.254.169.254 -j DROP 2>/dev/null || iptables -I DOCKER-USER -d 169.254.169.254 -j DROP
    docker run --rm --gpus all "$WORKER_IMAGE" nvidia-smi -L
  fi
fi
gpu=$(nvidia-smi --query-gpu=name,memory.total --format=csv,noheader | head -1)
step "GPU: $gpu"

# --- 4. weights, once ------------------------------------------------------------------------------
touch "$STATE/state/last-activity"   # the watchdog counts from here, not from the end of a long fetch
started=$(date +%s)
docker run --rm --name orazaka-fetch -v /models:/models "$WORKER_IMAGE" python -m app.fetch_models "$PROFILE"
step "weights ready in $(( $(date +%s) - started ))s"

# --- 5. the three containers -----------------------------------------------------------------------
docker network inspect orz >/dev/null 2>&1 || docker network create orz >/dev/null
for name in sd-server worker ollama relay; do docker rm -f "$name" >/dev/null 2>&1 || true; done

# SERVICES=video (GPU_SERVICES=video at start): a video batch on a 20 GB / 31 GB host keeps the memory for the video
# model — no image engine, no Ollama (a loaded Wan 2.2 5B takes ~22 GB of the host's RAM while it is offloaded).
if [[ "${SERVICES:-all}" != "video" ]]; then
docker run -d --name sd-server --network orz --restart unless-stopped --gpus all \
  -v /models:/models:ro -e MEDIA_IMAGE_OFFLOAD="$IMAGE_OFFLOAD" \
  "$WORKER_IMAGE" python -m app.sd_server "$IMAGE_MODEL" >/dev/null
fi

docker run -d --name worker --network orz --restart unless-stopped --gpus all --shm-size 8g \
  -v /models:/models:ro -v "$STATE/state":/var/lib/orazaka/state -v "$STATE/uploads":/var/lib/orazaka/uploads \
  --env-file "$CONF/worker.env" \
  -e MEDIA_GPU_PROFILE="$PROFILE" -e MEDIA_ACTIVITY_FILE=/var/lib/orazaka/state/last-activity \
  -v "$CONF/broker-ca.pem":/etc/orazaka/broker-ca.pem:ro \
  -e SPRING_RABBITMQ_HOST=relay -e SPRING_RABBITMQ_PORT=5671 -e SPRING_RABBITMQ_SSL_ENABLED=true \
  -e SPRING_RABBITMQ_SSL_CA_FILE=/etc/orazaka/broker-ca.pem -e SPRING_RABBITMQ_SSL_SERVER_NAME=broker-dev.orazaka.com \
  -e MEDIA_ASSET_SINK_URL=http://relay:8090 -e JOB_SERVICE_INTERNAL_URL=http://relay:8090 \
  -e ORAZAKA_MEDIA_MEM_HEADROOM_GB=4 \
  "$WORKER_IMAGE" >/dev/null

# Ollama: chat and vision for the Studios' text and photo steps while this GPU runs (the core reaches it as
# gpu-relay:11434). It unloads a model after 2 idle minutes so the video model gets the card back.
if [[ "${SERVICES:-all}" != "video" ]]; then
mkdir -p /models/ollama
docker run -d --name ollama --network orz --restart unless-stopped --gpus all \
  -v /models/ollama:/root/.ollama -e OLLAMA_KEEP_ALIVE=2m -e OLLAMA_NUM_PARALLEL=1 "$OLLAMA_IMAGE" >/dev/null
for _ in $(seq 1 30); do docker exec ollama ollama list >/dev/null 2>&1 && break; sleep 2; done
for model in $OLLAMA_MODELS; do docker exec ollama ollama pull "$model" >/dev/null && step "ollama: $model ready"; done
fi

# The chisel client: forwards the broker and the job service INTO this network, exposes sd-server and
# the video pipeline back inside the app. Its credential is read from the environment, not the argv.
docker run -d --name relay --network orz --restart unless-stopped --env-file "$CONF/relay.env" \
  "$CHISEL_IMAGE" client --keepalive 25s --max-retry-interval 30s "$RELAY_URL" \
  0.0.0.0:5671:broker-dev.orazaka.com:5671 0.0.0.0:8090:core:8090 \
  R:0.0.0.0:8086:sd-server:8086 R:0.0.0.0:8188:worker:8188 R:0.0.0.0:11434:ollama:11434 >/dev/null

touch "$STATE/ready" "$STATE/state/last-activity"
step "serving: sd-server ($IMAGE_MODEL), worker ($PROFILE), ollama ($OLLAMA_MODELS), relay → $RELAY_URL"
