# Concepts

Orazaka is an AI platform you run yourself. Your documents, prompts and results stay on your machine. There is no telemetry.

## Three layers

1. **Clients.** The web app, the mobile app and the `orazaka` command line. They never talk to a model directly.
2. **The engine.** One Java backend. It checks who you are, routes each request, and runs it through the pipeline.
3. **Workers and models.** Local models (through Ollama) answer chat. Workers do the heavy jobs, such as images and video, and report back.

## A request, step by step

You ask for something. The engine does five things, in this order:

1. It knows who you are and what your plan allows.
2. For a Studio run, it checks the content policy before any cost.
3. It enriches the prompt with context and picks a model.
4. It runs the job, on your hardware or on a provider you chose.
5. It writes a **run record**: what ran, where, how long, what it cost.

The ordered filters in steps 2 and 3 are the **interceptor pipeline**. A fixed core chain runs first, then the interceptors an operator configured in the database.

## Packs and Studios

- A **Studio** is a workflow: a few steps (model calls, transforms, human approvals) behind one form.
- A **pack** is a folder that ships Studios. Installing a pack adds data, not code.
- A **run** is one execution of a Studio. Every run has an id, a price and a result you can open again.

## Jobs and workers

Anything that takes more than a moment is a **job**. Jobs travel over a message queue to a **worker**, which can be written in any language. The queue contract is in the [worker protocol](WORKER_PROTOCOL.md).

## Who decides what

- **You, the operator,** decide the models, the plans, the content policy and who may sign in.
- **Nothing is refused by default** on a fresh install, except content that sexualises minors. That one is always refused.

## Where the details live

The reference pages are generated from the code, so they are always current: the [API](_generated/API_REFERENCE.md), the [CLI](_generated/CLI.md), the [models](_generated/MODELS.md), the [architecture](_generated/ARCHITECTURE.md) with its modules, interceptors, business layer and decision records.

## Next

- [Quickstart](101.md): run it in five minutes.
- [Run a Studio](STUDIO_GUIDE.md): see a run end to end.
- [Content policy](CONTENT_POLICY.md): what you can allow or refuse.
