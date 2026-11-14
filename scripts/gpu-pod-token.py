#!/usr/bin/env python3
"""A RunPod pod's own job service token (krizaka-internal#3645), asked of the job service by the launcher.

    gpu-pod-token.py mint KEY SECONDS   the pod's token (stdout only, never logged); its lifetime is the pod's cap
    gpu-pod-token.py revoke KEY         the terminated pod's token is refused from now on
    gpu-pod-token.py sweep [KEY...]     revokes the token of every pod not listed (the pods that still run)
    gpu-pod-token.py preflight          free journey, no pod: mint for a throwaway key, the four worker routes
                                        accept it, another route refuses it, revoke, then it is refused

The call carries GPU_SERVICE_TOKEN, a SERVICE token minted on this machine that never reaches a pod. The job
service is GPU_JOB_SERVICE_URL (the tests: a stub), else its internal port `core:8090` through the relay:
a chisel client to GPU_RELAY_URL with GPU_RELAY_AUTH (the relay credential this machine holds), opened for the
call and closed after it. Exit 0 on success; any refusal or unreachable service exits non-zero with one line.
"""
from __future__ import annotations

import json
import os
import secrets
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from collections.abc import Callable, Iterator
from contextlib import contextmanager

TIMEOUT = 15
TUNNEL_WAIT_SECONDS = 20


class PodTokenError(Exception):
    """The job service refused, or could not be reached."""


def call(base: str, service_token: str, method: str, path: str, body: dict | None = None,
         bearer: str | None = None) -> tuple[int, dict]:
    """One request to the job service; returns (status, JSON body or {})."""
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(  # noqa: S310 — the launcher's own tunnel or stub, never caller input
        base + path, data=data, method=method,
        headers={"Authorization": f"Bearer {bearer or service_token}", "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:  # noqa: S310
            raw = response.read()
            return response.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        return e.code, {}
    except (urllib.error.URLError, OSError) as e:
        raise PodTokenError(f"the job service is unreachable ({e.__class__.__name__})") from e


def expect(status: int, wanted: int, what: str) -> None:
    if status != wanted:
        raise PodTokenError(f"{what}: the job service answered {status}")


def mint(base: str, token: str, key: str, seconds: int) -> str:
    status, body = call(base, token, "POST", f"/internal/v1/gpu-pods/{key}/token", {"lifetimeSeconds": seconds})
    expect(status, 201, "mint")
    return body["token"]


def revoke(base: str, token: str, key: str) -> list[str]:
    status, body = call(base, token, "DELETE", f"/internal/v1/gpu-pods/{key}/token")
    expect(status, 200, "revoke")
    return body.get("revoked", [])


def sweep(base: str, token: str, running: list[str]) -> list[str]:
    status, body = call(base, token, "POST", "/internal/v1/gpu-pods/tokens/sweep", {"running": running})
    expect(status, 200, "sweep")
    return body.get("revoked", [])


def preflight(base: str, token: str, clock: Callable[[], float] = time.time) -> list[str]:
    """The free journey of the ticket, against the deployed job service; returns what it proved."""
    key = time.strftime("%Y%m%d%H%M%S", time.gmtime(clock())) + "-" + secrets.token_hex(3)
    pod = mint(base, token, key, 300)
    job = str(uuid.uuid4())
    proved = []
    routes = [("POST", "/internal/v1/workers/sovkora-preflight/heartbeat"),
              ("GET", f"/internal/v1/jobs/{job}/input/image")]
    for method, path in routes:
        status, _ = call(base, token, method, path, bearer=pod)
        if status in (401, 403):
            raise PodTokenError(f"the pod token was refused on {method} {path} ({status})")
        proved.append(f"accepted on {method} {path} ({status})")
    status, _ = call(base, token, "GET", "/internal/v1/capabilities/media.vision", bearer=pod)
    expect(status, 403, "the pod token on another internal route")
    proved.append("refused on GET /internal/v1/capabilities (403)")
    revoke(base, token, key)
    status, _ = call(base, token, *routes[0], bearer=pod)
    expect(status, 401, "the revoked pod token")
    proved.append("refused once revoked (401)")
    return proved


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def port_open(port: int) -> bool:
    with socket.socket() as s:
        s.settimeout(0.5)
        return s.connect_ex(("127.0.0.1", port)) == 0


@contextmanager
def job_service(env: dict, spawn: Callable[..., subprocess.Popen] = subprocess.Popen) -> Iterator[str]:
    """The job service's base URL: the given one, or a chisel tunnel to core:8090 for the duration."""
    if env.get("GPU_JOB_SERVICE_URL"):
        yield env["GPU_JOB_SERVICE_URL"].rstrip("/")
        return
    relay, auth = env.get("GPU_RELAY_URL"), env.get("GPU_RELAY_AUTH")
    if not relay or not auth:
        raise PodTokenError("no route to the job service (GPU_JOB_SERVICE_URL, or GPU_RELAY_URL + GPU_RELAY_AUTH)")
    port = free_port()
    try:
        # The credential travels in the child's environment (chisel reads AUTH), never on its command line.
        tunnel = spawn(["chisel", "client", "--keepalive", "25s", relay, f"127.0.0.1:{port}:core:8090"],
                       env={"PATH": env.get("PATH", ""), "AUTH": auth},
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except OSError as e:
        raise PodTokenError("chisel is not installed: the job service is reached through the relay") from e
    try:
        deadline = time.monotonic() + TUNNEL_WAIT_SECONDS
        while not port_open(port):
            if tunnel.poll() is not None or time.monotonic() > deadline:
                raise PodTokenError("the relay tunnel to the job service did not open")
            time.sleep(0.2)
        yield f"http://127.0.0.1:{port}"
    finally:
        tunnel.terminate()
        try:
            tunnel.wait(timeout=5)
        except subprocess.TimeoutExpired:
            tunnel.kill()


def main(argv: list[str], env: dict) -> int:
    command, args = (argv[1] if len(argv) > 1 else ""), argv[2:]
    token = env.get("GPU_SERVICE_TOKEN")
    if command not in ("mint", "revoke", "sweep", "preflight") or (command == "mint" and len(args) != 2) \
            or (command == "revoke" and len(args) != 1):
        print(__doc__, file=sys.stderr)
        return 2
    if not token:
        print("gpu-pod-token: no GPU_SERVICE_TOKEN", file=sys.stderr)
        return 1
    try:
        with job_service(env) as base:
            if command == "mint":
                print(mint(base, token, args[0], int(args[1])), end="")
            elif command == "revoke":
                revoke(base, token, args[0])
            elif command == "sweep":
                print("\n".join(sweep(base, token, args)))
            else:
                print("\n".join(preflight(base, token)))
    except PodTokenError as e:
        print(f"gpu-pod-token: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv, dict(os.environ)))
