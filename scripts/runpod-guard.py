#!/usr/bin/env python3
"""RunPod client and spend guard (SOV-096) on API v2 (orazaka#508: REST v1 and GraphQL stop on 2026-11-15).

  runpod-guard.py status            pods, uptime, GPU use, $/h  (needs AI_GPU_RUNPOD_API_KEY in the env)
  runpod-guard.py once              one pass: end the pods the rule below condemns
  runpod-guard.py loop [seconds]    `once` every 60 s (or the given interval) until interrupted
  runpod-guard.py decide < FILE     the rule alone, on a JSON fixture read from stdin (tests): prints the pods to end
  runpod-guard.py count             prints « pods: N running » (billing pods) and exits 3 when N > 0
  runpod-guard.py list [prefix]     one line per live pod: id status $/h gpu name
  runpod-guard.py create FILE       POST /v2/pods with the JSON body in FILE; prints « id cost gpu »
  runpod-guard.py get ID | ssh ID   the pod (JSON) | « host port » of its direct ssh
  runpod-guard.py terminate ID      POST /v2/pods/{id}/action {"action":"terminate"}: billing ends, the volume stays
  runpod-guard.py volume-id NAME DC | volume-create NAME DC GB   the models network volume
  runpod-guard.py balance           « balance spend/h » (the account balance has no v2 endpoint yet: GraphQL, until its sunset)

Rule: a RUNNING pod is ended when its GPU stayed under IDLE_UTIL % for IDLE_MINUTES (default 5 % / 20 min),
or when it has run MAX_HOURS (default 6 h) whatever it does. The end is a TERMINATE (GPU_GUARD_ACTION=stop keeps the
container disk): terminate is cheaper for occasional runs, the weights live on the network volume either way.
The key comes from the environment, never an argument. RUNPOD_API_BASE points the client at a stub in tests.
"""
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request

GRAPHQL = os.environ.get("RUNPOD_GRAPHQL_URL", "https://api.runpod.io/graphql")
API = os.environ.get("RUNPOD_API_BASE", "https://api.runpod.io/v2")
ACTION = os.environ.get("GPU_GUARD_ACTION", "terminate")
BILLING = ("PROVISIONING", "STARTING", "RUNNING")  # statuses that bill compute
IDLE_MINUTES = float(os.environ.get("GPU_IDLE_MINUTES", "20"))
MAX_HOURS = float(os.environ.get("GPU_MAX_HOURS", "6"))
IDLE_UTIL = float(os.environ.get("GPU_IDLE_UTIL", "5"))
STATE = os.path.expanduser(os.environ.get("GPU_LOCAL_DIR", "~/.orazaka/gpu-dev")) + "/runpod-idle.json"


def decide(pods, idle_since, now):
    """pods: [{id, name, uptime, util}] RUNNING only. Returns (to_stop [(id, reason)], new idle_since)."""
    stop, kept = [], {}
    for pod in pods:
        pid = pod["id"]
        if pod["util"] >= IDLE_UTIL:
            kept.pop(pid, None)
        else:
            kept[pid] = idle_since.get(pid, now)
        if pod["uptime"] >= MAX_HOURS * 3600:
            stop.append((pid, f"lived {pod['uptime'] / 3600:.1f} h (max {MAX_HOURS:g})"))
        elif pid in kept and now - kept[pid] >= IDLE_MINUTES * 60:
            stop.append((pid, f"GPU under {IDLE_UTIL:g}% for {(now - kept[pid]) / 60:.0f} min"))
    return stop, kept


USER_AGENT = "orazaka-runpod-guard/1.0 (+https://github.com/krizaka/orazaka)"  # urllib's default UA gets intermittent 403s
RETRY_STATUSES = {403, 429, 500, 502, 503, 504}
ATTEMPTS = 4


def call(url, key, body=None, method=None):
    """One API call. Retries 403/429/5xx with a growing pause (GPU_RETRY_SLEEP seconds, default 2); other errors raise."""
    pause = float(os.environ.get("GPU_RETRY_SLEEP", "2"))
    for attempt in range(1, ATTEMPTS + 1):
        request = urllib.request.Request(url, data=json.dumps(body).encode() if body else None, method=method,
                                         headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json",
                                                  "Accept": "application/json", "User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.loads(response.read() or b"{}")
        except urllib.error.HTTPError as error:
            if error.code not in RETRY_STATUSES or attempt == ATTEMPTS:
                raise
            time.sleep(pause * attempt)


def pods_all(key):
    """Every live pod of the account (v2 answers {"pods": [...], "pagination": {...}}), following the cursor."""
    out, cursor = [], None
    while True:
        page = call(f"{API}/pods" + (f"?cursor={cursor}" if cursor else ""), key)
        out += page.get("pods") or []
        pagination = page.get("pagination") or {}
        cursor = pagination.get("nextCursor")
        if not pagination.get("hasNextPage") or not cursor:
            return [p for p in out if p.get("status") != "TERMINATED"]


def running(key):
    """RUNNING pods with uptime and GPU use. The list carries no runtime: each running pod is fetched by id."""
    out = []
    for pod in pods_all(key):
        if pod.get("status") != "RUNNING":
            continue
        runtime = call(f"{API}/pods/{pod['id']}", key).get("runtime") or {}
        out.append({"id": pod["id"], "name": pod.get("name", ""), "cost": pod.get("cost") or 0,
                    "uptime": runtime.get("uptime") or 0,
                    "util": max([g.get("util") or 0 for g in runtime.get("gpus") or []] or [0])})
    return out


def act(key, pod_id, action="terminate"):
    return call(f"{API}/pods/{pod_id}/action", key, {"action": action}, "POST")


def load_state():
    try:
        return json.load(open(STATE))
    except (OSError, ValueError):
        return {}


def once(key):
    pods = running(key)
    to_stop, idle = decide(pods, load_state(), time.time())
    os.makedirs(os.path.dirname(STATE), exist_ok=True)
    json.dump(idle, open(STATE, "w"))
    for pid, reason in to_stop:
        act(key, pid, ACTION)
        print(f"runpod-guard: {'stopped' if ACTION == 'stop' else 'terminated'} {pid} — {reason}", flush=True)
    return len(to_stop)


def cmd_count(key, _args):
    live = [p for p in pods_all(key) if p.get("status") in BILLING]
    print(f"pods: {len(live)} running")
    return 3 if live else 0


def cmd_list(key, args):
    for p in pods_all(key):
        if p.get("name", "").startswith(args[0] if args else ""):
            print(p["id"], p.get("status"), p.get("cost") or 0, (p.get("gpu") or {}).get("id", "?"), p.get("name", ""))
    return 0


def cmd_create(key, args):
    pod = call(f"{API}/pods", key, json.load(open(args[0])), "POST")
    print(pod["id"], pod.get("cost", ""), (pod.get("gpu") or {}).get("id", ""))
    return 0


def cmd_get(key, args):
    print(json.dumps(call(f"{API}/pods/{args[0]}", key)))
    return 0


def cmd_ssh(key, args):
    direct = (call(f"{API}/pods/{args[0]}", key).get("ssh") or {}).get("direct") or {}
    print(direct.get("host", ""), direct.get("port", ""))
    return 0


def cmd_terminate(key, args):
    act(key, args[0], "terminate")
    return 0


def cmd_volume_id(key, args):
    name, dc = args[0], args[1]
    volumes = call(f"{API}/network-volumes", key).get("networkVolumes") or []
    print(next((v["id"] for v in volumes if v.get("name") == name and v.get("dataCenter") == dc), ""))
    return 0


def cmd_volume_create(key, args):
    body = {"name": args[0], "dataCenter": args[1], "size": int(args[2])}
    print(call(f"{API}/network-volumes", key, body, "POST")["id"])
    return 0


def cmd_balance(key, _args):
    me = call(GRAPHQL, key, {"query": "{ myself { clientBalance currentSpendPerHr } }"})["data"]["myself"]
    print(me["clientBalance"], me["currentSpendPerHr"])
    return 0


def create_body(e, gpu_id):
    """The v2 body of one orazaka pod: gpu / mounts / disk nested, the start command as entrypoint + cmd. `e` is the environment."""
    boot = base64.b64encode(open(e["BOOT"], "rb").read()).decode()
    if e.get("MEASURE"):
        # Measurement only: the pod never learns the relay, the broker or a service token.
        env = {k: e[k] for k in ("WORKER_REF", "PROFILE", "MAX_HOURS", "IDLE_MINUTES", "MEASURE_ARGS", "MEASURE_PIP")}
    else:
        env = {k: e[k] for k in ("WORKER_REF", "PROFILE", "RELAY_URL", "RELAY_AUTH", "RABBIT_PASSWORD", "SERVICE_TOKEN", "BROKER_CA_B64", "MAX_HOURS", "IDLE_MINUTES")}
    if e.get("GPU_OUT_URL") and not e.get("MEASURE"):
        # The pod's own output zone (krizaka-internal#2121): its URL under <day>/<run>/<pod>, the header name and THAT zone's key.
        # Never a site's key: the dedicated zone is the only storage a rented machine can touch.
        for k in ("GPU_OUT_URL", "GPU_OUT_HEADER", "GPU_OUT_KEY"):
            env[k] = e[k]
    if e.get("STILLS"):
        env["STILLS"] = "1"  # the image engine only (runpod-bootstrap.sh): a render batch never waits for video weights
    if e.get("HF_TOKEN"):
        env["HF_TOKEN"] = e["HF_TOKEN"]  # downloads of public weights, faster and not rate-limited
    env["PUBLIC_KEY"] = e["PUBKEY"]
    env["ORAZAKA_BOOT_B64"] = boot
    return {
        "name": "orazaka-gpu-" + ("measure" if e.get("MEASURE") else e["PROFILE"]),
        "image": e["RP_IMAGE"],
        "cloud": "SECURE",
        "gpu": {"id": gpu_id, "count": 1},
        "dataCenterIds": [e["DC"]],
        # No volume (GPU_RUNPOD_VOLUME=none): the weights are fetched onto the container disk at each start (orazaka#508).
        **({"mounts": {"network": [{"volumeId": e["VOLUME"], "path": "/models"}]}} if e.get("VOLUME") else {}),
        # A model kept on the host's own disk (`cache: scratch`: the I2V A14B, 115 GB) needs room: GPU_RUNPOD_DISK_GB.
        "disk": int(e.get("DISK_GB") or 60),
        "ports": ["22/tcp"],
        "env": env,
        # The image's own start script (sshd with PUBLIC_KEY), then this worker's boot beside it.
        "entrypoint": ["bash", "-c"],
        "cmd": ["echo \"$ORAZAKA_BOOT_B64\" | base64 -d > /orazaka-boot.sh; (bash /orazaka-boot.sh &) ; exec /start.sh"],
    }


def cmd_body(_key, args):
    fd = os.open(args[0], os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as handle:
        json.dump(create_body(os.environ, args[1]), handle)
    return 0


CLIENT = {"body": cmd_body, "count": cmd_count, "list": cmd_list, "create": cmd_create, "get": cmd_get, "ssh": cmd_ssh,
          "terminate": cmd_terminate, "volume-id": cmd_volume_id, "volume-create": cmd_volume_create,
          "balance": cmd_balance}


def main(argv):
    cmd = argv[1] if len(argv) > 1 else "status"
    if cmd == "decide":
        fixture = json.load(sys.stdin)
        stop, _ = decide(fixture["pods"], fixture.get("idle_since", {}), fixture["now"])
        print(json.dumps(stop))
        return 0
    key = os.environ.get("AI_GPU_RUNPOD_API_KEY", "")
    if not key:
        print("runpod-guard: AI_GPU_RUNPOD_API_KEY is not in the environment", file=sys.stderr)
        return 1
    if cmd == "status":
        pods = running(key)
        for pod in pods:
            print(f"{pod['name']} {pod['id']} up {pod['uptime'] / 3600:.1f} h GPU {pod['util']:g}% ${pod['cost']}/h")
        print(f"{len(pods)} running, ${sum(p['cost'] for p in pods):.2f}/h")
    elif cmd == "once":
        once(key)
    elif cmd in CLIENT:
        try:
            return CLIENT[cmd](key, argv[2:])
        except urllib.error.HTTPError as error:
            print(f"runpod: {cmd} answered {error.code}: {error.read()[:200].decode(errors='replace')}", file=sys.stderr)
            return 1
    elif cmd == "loop":
        every = float(argv[2]) if len(argv) > 2 else 60
        while True:
            try:
                once(key)
            except Exception as error:  # a network blip must not end the guard
                print(f"runpod-guard: {error}", file=sys.stderr, flush=True)
            time.sleep(every)
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
