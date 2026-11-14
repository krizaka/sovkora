"""A GPU pod's own broker user (krizaka-internal#3614), against a real RabbitMQ (the broker's image, rabbitmq:4.2).

``scripts/gpu-pod-broker.py`` makes the user the launcher gives a pod and deletes it when the pod ends. These tests run
it against a RabbitMQ container and log in as the pod, with this worker's own connection code: the worker's topology
works under the scope, everything else of the platform is refused, and once the pod is ended its user is refused and
its open connection closed. Docker is required (the CI runner has it): no Docker is a failure, never a skip.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import time
import unittest
import uuid
from pathlib import Path

import pika
import pika.exceptions

from app import broker

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "scripts" / "gpu-pod-broker.py"
IMAGE = os.environ.get(
    "GPU_POD_BROKER_TEST_IMAGE", "rabbitmq:4.2-alpine"
)  # infra/deploy/broker/cloud-init.yaml
KEY = "20261114120000-a1b2c3"
ADMIN = ("platform", "platform-test-password")


def _run(*args: str, stdin: str | None = None) -> subprocess.CompletedProcess:
    return subprocess.run(
        list(args), input=stdin, capture_output=True, text=True, check=False, timeout=180
    )


class PodBrokerUser(unittest.TestCase):
    """One container for the class: the platform's topology made by an admin, then the pod's user."""

    @classmethod
    def setUpClass(cls):
        # test_main replaces sys.modules["pika"] with a mock; this suite must hold the real client (it runs first in CI).
        assert isinstance(pika.BlockingConnection, type), (
            "pika is mocked: run this suite before test_main"
        )
        assert shutil.which("docker"), (
            "docker is required for the broker integration test (no skip)"
        )
        cls.container = f"sovkora-pod-broker-{uuid.uuid4().hex[:8]}"
        started = _run(
            "docker", "run", "-d", "--name", cls.container, "-p", "127.0.0.1::5672", IMAGE
        )
        assert started.returncode == 0, started.stderr
        cls.shell = f"docker exec -i {cls.container} sh -s"
        # Wait for the server's own word: a rabbitmqctl started before the node is up can break its distribution start.
        deadline = time.monotonic() + 120
        while True:
            ready = _run("docker", "logs", cls.container)
            if (
                "Server startup complete" in ready.stdout + ready.stderr
                or time.monotonic() > deadline
            ):
                break
            time.sleep(1)
        ready = _run(
            "docker", "exec", cls.container, "rabbitmqctl", "await_startup", "--timeout", "60"
        )
        assert ready.returncode == 0, ready.stderr
        port = (
            _run("docker", "port", cls.container, "5672/tcp")
            .stdout.strip()
            .splitlines()[0]
            .rsplit(":", 1)[1]
        )
        cls.port = int(port)
        made = _run(
            "docker",
            "exec",
            "-i",
            cls.container,
            "sh",
            "-s",
            stdin=(
                f"set -e\nrabbitmqctl add_user {ADMIN[0]} {ADMIN[1]} >/dev/null\n"
                f"rabbitmqctl -q set_permissions -p / {ADMIN[0]} '.*' '.*' '.*'\n"
            ),
        )
        assert made.returncode == 0, made.stderr
        # The platform's topology, as the Java services declare it (AGENTS.md §6): the pod never makes these.
        with cls._connect(*ADMIN) as connection:
            channel = connection.channel()
            channel.exchange_declare(exchange="orazaka.jobs", exchange_type="topic", durable=True)
            channel.exchange_declare(exchange="orazaka.dlx", exchange_type="direct", durable=True)
            channel.exchange_declare(exchange="orazaka.events", exchange_type="topic", durable=True)
            channel.queue_declare(queue="orazaka.jobs.interactive", durable=True)
            channel.queue_bind(
                queue="orazaka.jobs.interactive", exchange="orazaka.jobs", routing_key="job.text.*"
            )

    @classmethod
    def tearDownClass(cls):
        _run("docker", "rm", "-f", cls.container)

    @classmethod
    def _connect(cls, user: str, password: str) -> pika.BlockingConnection:
        env = {
            "SPRING_RABBITMQ_HOST": "127.0.0.1",
            "SPRING_RABBITMQ_PORT": str(cls.port),
            "SPRING_RABBITMQ_USERNAME": user,
            "SPRING_RABBITMQ_PASSWORD": password,
        }
        return pika.BlockingConnection(broker.connection_parameters(env))

    def _tool(self, *args: str) -> subprocess.CompletedProcess:
        return subprocess.run(
            [sys.executable, str(TOOL), *args],
            capture_output=True,
            text=True,
            check=False,
            timeout=180,
            env={**os.environ, "GPU_BROKER_SHELL": self.shell},
        )

    def _pod(self, key: str = KEY) -> tuple[str, str]:
        made = self._tool("create", key)
        self.assertEqual(made.returncode, 0, made.stderr)
        user, password = made.stdout.split()
        self.assertEqual(user, f"gpu-pod-{key}")
        return user, password

    def _refused(self, action) -> None:
        """Runs ``action(channel)`` on a fresh channel of the pod and expects the broker's 403."""
        connection = self._connect(*self.pod)
        try:
            with self.assertRaises(pika.exceptions.ChannelClosedByBroker) as closed:
                channel = connection.channel()
                channel.confirm_delivery()
                action(channel)
            self.assertEqual(closed.exception.reply_code, 403, closed.exception.reply_text)
        finally:
            if connection.is_open:
                connection.close()

    # -- the scope ----------------------------------------------------------------------------------------------------

    def test_1_the_worker_topology_runs_under_the_pod_scope(self):
        self.pod = self._pod()
        connection = self._connect(*self.pod)
        try:
            channel = connection.channel()
            channel.confirm_delivery()
            passive = {"MEDIA_BROKER_EXCHANGES_PASSIVE": "true"}
            # consumer._consume_once, job_control._listen_once and telemetry._publish_job_event, step by step
            broker.declare_exchange(channel, "orazaka.jobs", "topic", passive)
            broker.declare_exchange(channel, "orazaka.dlx", "direct", passive)
            broker.declare_exchange(channel, "orazaka.events", "topic", passive)
            arguments = {
                "x-max-length": 1000,
                "x-overflow": "reject-publish",
                "x-dead-letter-exchange": "orazaka.dlx",
            }
            for lane in ("orazaka.jobs.video", "orazaka.jobs.sound"):
                channel.queue_declare(
                    queue=lane,
                    durable=True,
                    arguments={**arguments, "x-dead-letter-routing-key": lane},
                )
                channel.queue_bind(
                    queue=lane,
                    exchange="orazaka.jobs",
                    routing_key="job." + lane.rsplit(".", 1)[1] + ".*",
                )
                channel.queue_declare(queue=lane + ".dlq", durable=True)
                channel.queue_bind(queue=lane + ".dlq", exchange="orazaka.dlx", routing_key=lane)
            channel.basic_consume(queue="orazaka.jobs.video", on_message_callback=lambda *_: None)
            cancel = f"orazaka.cancel.{uuid.uuid4().hex}"
            channel.queue_declare(queue=cancel, exclusive=True, auto_delete=True)
            channel.queue_bind(queue=cancel, exchange="orazaka.jobs", routing_key="job.cancel.*")
            for event in ("progress", "done", "error", "cancelled"):
                channel.basic_publish(
                    exchange="orazaka.events", routing_key=f"job.job-1.{event}", body=b"{}"
                )
        finally:
            connection.close()

    def test_2_the_rest_of_the_platform_is_refused(self):
        self.pod = self._pod("20261114120001-a1b2c3")
        try:
            # A shared exchange is the platform's: declaring it for real is the right to delete it.
            self._refused(
                lambda c: c.exchange_declare(
                    exchange="orazaka.jobs", exchange_type="topic", durable=True
                )
            )
            self._refused(lambda c: c.exchange_delete(exchange="orazaka.jobs"))
            # Another lane's queue: neither read nor removed.
            self._refused(
                lambda c: c.basic_consume(
                    queue="orazaka.jobs.interactive", on_message_callback=lambda *_: None
                )
            )
            self._refused(lambda c: c.queue_delete(queue="orazaka.jobs.interactive"))
            self._refused(
                lambda c: c.queue_declare(queue="orazaka.billing.settlements", durable=True)
            )
            # Only the job lifecycle keys on orazaka.events: no forged billing or capability event, nothing on orazaka.jobs.
            self._refused(
                lambda c: c.basic_publish(
                    exchange="orazaka.events", routing_key="evt.billing.settled", body=b"{}"
                )
            )
            self._refused(
                lambda c: c.basic_publish(
                    exchange="orazaka.events", routing_key="evt.capability.changed", body=b"{}"
                )
            )
            self._refused(
                lambda c: c.basic_publish(
                    exchange="orazaka.jobs", routing_key="job.text.generate", body=b"{}"
                )
            )
        finally:
            self._tool("delete", "20261114120001-a1b2c3")

    # -- the end of the pod -------------------------------------------------------------------------------------------

    def test_3_an_ended_pod_is_refused_and_its_open_connection_closed(self):
        key = "20261114120002-a1b2c3"
        user, password = self._pod(key)
        held = self._connect(user, password)
        ended = self._tool("delete", key)
        self.assertEqual(ended.returncode, 0, ended.stderr)
        with self.assertRaises(pika.exceptions.ProbableAuthenticationError):
            self._connect(user, password)
        deadline = time.monotonic() + 15
        with self.assertRaises(pika.exceptions.AMQPConnectionError):
            while time.monotonic() < deadline:
                held.process_data_events(time_limit=0.5)
        self.assertFalse(held.is_open)

    def test_4_the_sweep_ends_the_users_of_pods_that_are_gone_and_keeps_the_live_ones(self):
        live, gone = "20261114120003-a1b2c3", "20261114120004-a1b2c3"
        self._pod(live)
        gone_user, gone_password = self._pod(gone)
        swept = self._tool("sweep", f"orazaka-gpu-big-{live}")
        self.assertEqual(swept.returncode, 0, swept.stderr)
        self.assertIn(f"gpu-pod-{gone}", swept.stdout)
        self.assertNotIn(f"gpu-pod-{live}", swept.stdout)
        with self.assertRaises(pika.exceptions.ProbableAuthenticationError):
            self._connect(gone_user, gone_password)
        self._tool("delete", live)

    def test_5_a_refused_create_prints_no_credential(self):
        key = "20261114120005-a1b2c3"
        self._pod(key)
        made = self._tool(
            "create", key
        )  # the user exists: rabbitmqctl refuses, the launcher gets nothing to hand out
        self._tool("delete", key)
        self.assertEqual(made.returncode, 1)
        self.assertNotIn("PASSWORD", made.stderr)
        self.assertEqual(made.stdout, "")


if __name__ == "__main__":
    unittest.main()
