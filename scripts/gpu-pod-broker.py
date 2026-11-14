#!/usr/bin/env python3
"""The broker user of one GPU pod (krizaka-internal#3614): made when the pod is started, deleted when it ends.

  gpu-pod-broker.py create KEY          adds the user gpu-pod-KEY with a fresh password and the pod's scope only;
                                        prints « user password » on stdout (for the launcher's $(…), never echoed)
  gpu-pod-broker.py delete KEY          deletes gpu-pod-KEY, then closes whatever connection it still holds
  gpu-pod-broker.py sweep [LIVE …]      deletes every gpu-pod-* user whose key ends none of the LIVE pod names
                                        (a pod its own watchdog or the spend guard ended)
  gpu-pod-broker.py scope               prints the permissions a pod user gets (the tests read them)

Everything goes through RabbitMQ's own CLI, rabbitmqctl, run by the shell GPU_BROKER_SHELL names: a command that runs
`sh -s` where rabbitmqctl is — `ssh root@<broker> docker exec -i rabbitmq sh -s` (gpu-dev.sh),
`docker exec -i <c> sh -s` in the tests. The script travels on stdin, the password in a here-document: never an
argument, never on a command line of either machine. The admin side (the ssh key that reaches the broker) never
leaves this machine.

The scope, against the shared gpu-worker user (configure, write and read on every name of the platform, deletion
included): the pod may declare, bind and consume only the media worker's own lanes and its own cancel queue, check the
shared exchanges exist without the right to change them, and publish on the events exchange only the job lifecycle keys
the worker sends (topic permissions).
"""

import os
import re
import secrets
import shlex
import subprocess
import sys

PREFIX = "gpu-pod-"
VHOST = "/"
KEY = re.compile(r"^[0-9]{14}-[0-9a-f]{6}$")
# The names, as the worker uses them (workers/media/app: consumer.py, job_control.py, telemetry.py).
JOBS, DLX, EVENTS = "orazaka.jobs", "orazaka.dlx", "orazaka.events"
LANES = tuple(f"{JOBS}.{lane}" for lane in ("video", "sound"))
CANCEL = JOBS.replace("jobs", "cancel") + "."  # + 32 hex digits: job_control's own exclusive queue


def _names(*names):
    return "|".join(re.escape(n) for n in names)


_OWN = rf"({_names(*LANES)})(\.dlq)?|{re.escape(CANCEL)}[0-9a-f]{{32}}"
#: configure / write / read, vhost-wide (rabbitmqctl set_permissions).
SCOPE = {
    "configure": rf"^({_OWN})$",
    # The dead-letter exchange: RabbitMQ asks for write on it when a queue that names it is declared.
    "write": rf"^({_OWN}|{_names(EVENTS, DLX)})$",
    "read": rf"^({_OWN}|{_names(JOBS, DLX)})$",
}
#: write / read of routing keys on the events exchange (rabbitmqctl set_topic_permissions).
TOPIC = {"write": r"^job\.[A-Za-z0-9-]+\.(progress|done|error|cancelled)$", "read": r"^$"}


class BrokerError(RuntimeError):
    """The broker refused or could not be reached; the message never carries a password."""


def user_of(key):
    if not KEY.match(key):
        raise BrokerError(f"a pod key is <UTC yyyymmddHHMMSS>-<6 hex>, not {key!r}")
    return PREFIX + key


def _shell(script):
    command = shlex.split(os.environ.get("GPU_BROKER_SHELL", ""))
    if not command:
        raise BrokerError("GPU_BROKER_SHELL is not set: no way to reach the broker's rabbitmqctl")
    try:
        # The operator's own command (GPU_BROKER_SHELL), never a caller's input.
        done = subprocess.run(command, input=script, capture_output=True, text=True, check=False, timeout=120)  # noqa: S603
    except subprocess.TimeoutExpired as error:
        raise BrokerError("rabbitmqctl did not answer in 120 s") from error
    if done.returncode != 0:
        raise BrokerError(f"rabbitmqctl failed ({done.returncode}): {done.stderr.strip()[-300:]}")
    return done.stdout


def create_script(user, password):
    q = shlex.quote
    return "\n".join(
        [
            "set -e",
            # Not -q: quiet, add_user stops reading the password from stdin (it waits for a terminal).
            f"rabbitmqctl add_user {q(user)} >/dev/null <<'PASSWORD'",
            password,
            "PASSWORD",
            f"rabbitmqctl -q set_permissions -p {q(VHOST)} {q(user)} "
            f"{q(SCOPE['configure'])} {q(SCOPE['write'])} {q(SCOPE['read'])}",
            f"rabbitmqctl -q set_topic_permissions -p {q(VHOST)} {q(user)} {q(EVENTS)} "
            f"{q(TOPIC['write'])} {q(TOPIC['read'])}",
            "",
        ]
    )


def delete_script(user):
    q = shlex.quote
    # Delete first: from then on a reconnect is refused; then the connections still open are closed.
    return "\n".join(
        [
            "set -e",
            f"if rabbitmqctl -q list_users --no-table-headers | cut -f1 | grep -qxF {q(user)}; then",
            f"  rabbitmqctl -q delete_user {q(user)}",
            f"  rabbitmqctl -q close_all_user_connections {q(user)} 'the GPU pod ended' || true",
            "fi",
            "",
        ]
    )


def create(key):
    user = user_of(key)
    password = secrets.token_urlsafe(32)
    _shell(create_script(user, password))
    return user, password


def delete(key):
    _shell(delete_script(user_of(key)))


def pod_users():
    listed = _shell("set -e\nrabbitmqctl -q list_users --no-table-headers\n")
    names = (line.split("\t")[0].strip() for line in listed.splitlines())
    return sorted(name for name in names if name.startswith(PREFIX))


def orphans(users, live_names):
    """The pod users no live pod carries: a live pod's name ends with its key."""
    return [u for u in users if not any(name.endswith("-" + u[len(PREFIX) :]) for name in live_names)]


def sweep(live_names):
    gone = orphans(pod_users(), live_names)
    for user in gone:
        _shell(delete_script(user))
    return gone


def main(argv):
    cmd, args = (argv[1] if len(argv) > 1 else ""), argv[2:]
    try:
        if cmd == "create" and len(args) == 1:
            print(*create(args[0]))
        elif cmd == "delete" and len(args) == 1:
            delete(args[0])
        elif cmd == "sweep":
            for user in sweep(args):
                print(f"deleted {user} (no live pod)")
        elif cmd == "scope":
            for name, value in {**SCOPE, **{f"topic-{k}": v for k, v in TOPIC.items()}}.items():
                print(name, value)
        else:
            print("\n".join(__doc__.strip().splitlines()[2:8]), file=sys.stderr)
            return 2
    except BrokerError as error:
        print(f"gpu-pod-broker: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
