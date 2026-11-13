#!/usr/bin/env python3
"""The tenant-screening worker: the deterministic half of the saga (plan, consent, stages, cut-offs, report).

Tier-W: it speaks only AMQP and calls no model. The log line is the job id and the routing key, never a name, an outcome or a number.
"""

import json
import os

import pika
import yaml

import broker
import handlers
import screening
import worker_failure

with open(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "worker.yaml"), encoding="utf-8"
) as declaration_file:
    DECLARATION = yaml.safe_load(declaration_file)
DLX_EXCHANGE = "orazaka.dlx"
EXCHANGES = {"orazaka.jobs": "topic", "orazaka.events": "topic", DLX_EXCHANGE: "direct"}
QUEUE = f"orazaka.jobs.{DECLARATION['family']}"
INPUT_REFUSED = "INPUT_REFUSED"
PROVIDER_NOT_ENABLED = "PROVIDER_NOT_ENABLED"
REFUSALS = (screening.InvalidInput,)  # ProviderNotEnabled is one


class Refusal(worker_failure.InvalidJobPayload):
    """A refused payload — the one cause that bills. Its reason is a code: the rules' own messages name the stage and the
    rule the caller wrote, and what the caller wrote is theirs, not the log's nor the saga's."""

    REASONS = frozenset({INPUT_REFUSED, PROVIDER_NOT_ENABLED})

    @classmethod
    def of(cls, bad: "screening.InvalidInput") -> "Refusal":
        return cls(PROVIDER_NOT_ENABLED if isinstance(bad, screening.ProviderNotEnabled) else INPUT_REFUSED)


class Consumer:
    """Takes one job at a time off the queue, runs its handler, and tells the saga how it went."""

    def __init__(self, channel):
        self.channel = channel
        self.done: set = set()

    def tell(self, job_id: str, verdict: str, body: dict) -> None:
        properties = pika.BasicProperties(content_type="application/json", delivery_mode=2)
        self.channel.basic_publish(
            "orazaka.events", f"job.{job_id}.{verdict}", json.dumps(dict(body, jobId=job_id)).encode(), properties
        )

    def work(self, key: str, job: dict) -> None:
        job_id = job.get("jobId")
        print(f"[screening] {key} -> job {job_id}", flush=True)
        try:
            result, used = handlers.handle(key, job.get("payload"))
            self.tell(job_id, "done", {"result": result, "consumption": used})
        except REFUSALS as bad:
            self.fail(key, job_id, Refusal.of(bad))
        except Exception as failure:  # noqa: BLE001 — any failure must reach the saga, not the log alone
            self.fail(key, job_id, failure)

    def fail(self, key: str, job_id, failure: Exception) -> None:
        """A coded reason or a type name, in the event and in the log: never a message."""
        print(worker_failure.failure_log("screening", key, job_id, failure), flush=True)
        self.tell(job_id, "error", worker_failure.failure_answer(job_id, failure))

    def __call__(self, channel, method, properties, body) -> None:
        try:
            job = json.loads(body)
        except json.JSONDecodeError:
            return channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
        identity = properties.message_id or job.get("jobId")
        if identity not in self.done:
            self.work(method.routing_key, job)
            self.done.add(identity)
        channel.basic_ack(delivery_tag=method.delivery_tag)


def connect() -> "pika.adapters.blocking_connection.BlockingChannel":
    password = (
        os.environ.get("RABBITMQ_PASS")
        or os.environ.get("RABBITMQ_PASSWORD")
        or os.environ.get("SPRING_RABBITMQ_PASSWORD")
    )
    params = broker.connection_parameters(
        pika.PlainCredentials(os.environ.get("RABBITMQ_USER", "guest"), password or "guest")
    )
    print(f"[screening] broker {broker.describe(params)}", flush=True)
    channel = pika.BlockingConnection(params).channel()
    for name, kind in EXCHANGES.items():
        channel.exchange_declare(exchange=name, exchange_type=kind, durable=True)
    dead = {"x-dead-letter-exchange": DLX_EXCHANGE, "x-dead-letter-routing-key": QUEUE}
    channel.queue_declare(queue=QUEUE, durable=True, arguments=dead)
    channel.queue_declare(queue=f"{QUEUE}.dlq", durable=True)
    channel.queue_bind(queue=f"{QUEUE}.dlq", exchange=DLX_EXCHANGE, routing_key=QUEUE)
    for binding in DECLARATION["bindings"]:
        channel.queue_bind(queue=QUEUE, exchange="orazaka.jobs", routing_key=binding)
    channel.basic_qos(prefetch_count=DECLARATION.get("concurrency", 1))
    return channel


if __name__ == "__main__":
    ready = connect()
    ready.basic_consume(queue=QUEUE, on_message_callback=Consumer(ready))
    print(f"[screening] consuming {QUEUE}", flush=True)
    ready.start_consuming()
