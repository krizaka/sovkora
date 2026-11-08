---
title: Sovkora UI Reference
description: Documentation for Sovkora UI Reference
category: UI Guidelines
order: 13
---
# Sovkora UI Reference

The Sovkora platform includes a comprehensive client tier housed under `apps/` as npm workspaces. The primary web client is a Next.js 16 application designed with cinematic dark-mode and glassmorphism. This document maps out the available interfaces, workflows, and their connections to the backend.

## 1. Authentication & Identity

The authentication pages provide a secure ingress to the Sovkora Gateway, issuing secure JWTs and HTTP-only cookies.

*   **Login (`/login`)**: Secure entry point for returning users.
*   **Register (`/register`)**: Account creation flow for new users. Includes password strength validation.
*   **Forgot Password (`/forgot-password`)**: Trigger the password recovery workflow.
*   **Profile (`/profile`)**: Manage your personal information, update passwords, and view session history.
*   **Settings (`/settings`)**: Configure UI preferences (theme, language), AI model defaults, and manage API keys for external MCP servers.

## 2. Playgrounds (The AI Engine)

The Playgrounds are the interactive workspaces where you can directly interact with the Sovkora Unified Engine. They are located under `/playground/`.

### Text & Chat
*   **Text Chat (`/playground/text/chat`)**: The main interface for interacting with LLMs. Supports multi-turn conversations, persona selection, and real-time streaming responses via Server-Sent Events (SSE). 

### Image Generation
*   **Image Studio (`/playground/image/generate`)**: A highly visual interface for generating images using local Stable Diffusion models. Supports prompt input, negative prompts, aspect ratio selection, and seed configuration.

### Video Synthesis
*   **Video Generation (`/playground/video/generate`)**: Interface to orchestrate the video worker (e.g., Stable Video Diffusion). Input an initial image or prompt and generate cinematic sequences.
*   **Video Analysis (`/playground/video/analyze`)**: Upload videos and use vision models to extract metadata, summarize content, or detect specific elements.

### Audio & Speech
*   **Speech Synthesis (`/playground/speech/synthesis`)**: Text-to-Speech (TTS) interface. Generate high-quality voice output from text prompts.
*   **Audio Analysis (`/playground/audio/analyze`)**: Speech-to-Text (STT) interface. Upload audio files to generate transcripts using local Whisper models.

### Vision
*   **Vision Analysis (`/playground/vision/analyze`)**: Upload images and ask questions about them. Uses multimodal LLMs (like LLaVA) to understand and describe visual content.

### Code
*   **Code Scaffold (`/playground/code/scaffold`)**: A specialized workspace for generating, reviewing, and iterating on code snippets with AI assistance.

## 3. Automation & Agents

*   **Automation Hub (`/automation`)**: A protected area where you can define and schedule autonomous background tasks. Workflows defined here are dispatched to the `orazaka-workers/external-services` module.

## 4. Dashboards (Administration)

Administrative panels designed for platform operators to monitor the local infrastructure.

*   **Jobs Dashboard (`/dashboard/jobs`)**: Real-time monitoring of asynchronous tasks (like video rendering or long-running agent tasks). Displays task statuses (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`), execution time, and logs.
*   **Admin Dashboard (`/dashboard/admin`)**: High-level overview of the system topology. Monitor the health of the PostgreSQL database, Redis caches, RabbitMQ queues, and GPU worker nodes.

## 5. Marketing & Informational

These pages serve to present the value of Sovkora to prospective users.

*   **Home (`/`)**: The main landing page showcasing the product's value proposition.
*   **Unified Engine (`/features/unified-engine`)**: Detailed explanation of the multi-modal orchestration capabilities.
*   **Absolute Privacy (`/features/absolute-privacy`)**: Deep-dive into the local-first, zero-cloud architecture.
*   **Infinite Reach (`/features/infinite-reach`)**: Explanation of the Tool Calling and MCP (Model Context Protocol) integration.
*   **Legal:** Privacy Policy (`/privacy`), Terms of Service (`/terms`), Contact (`/contact`).

---

## Technical Dependencies

The UI interacts with the backend strictly through the Next.js API Routes (`src/app/api/**`). The browser **never** makes direct requests to the Gateway (8080) or Ollama (11434).

*   **Input Blocking:** To ensure stability, the UI automatically locks text inputs and buttons when a request `isGenerating` or `isSending` (ERR-126).
*   **Date Formatting:** All dates are formatted on the client using `date-fns` to avoid hydration mismatches (ERR-108).
*   **Icons:** Provided by a centralized `Lucide` icon library (`Icon.tsx`).
*   **Form Events:** `onSubmit` handlers use React 19+ native types (`React.SubmitEventHandler<HTMLFormElement>`).

---

## Workspace Architecture

All client packs are orchestrated by the workspace root at `apps/package.json`:

| Package | Location | Purpose | Port |
|:---|:---|:---|:---:|
| `orazaka-web-client` | `apps/web/` | Client-facing Next.js 16 App Router application | 3000 |
| `orazaka-web-client` (console, `src/app/console`, host `admin.*`) | `apps/web/` | Isolated SecOps Administration Console (the former `orazaka-web-admin`) | same app |
| `orazaka-mobile-client` | `apps/mobile/` | Expo SDK 53 cross-platform mobile app — 15 screens, wired to the **edge** (`:8088`) with a bearer JWT; auth, chat SSE, Studio, jobs, connectors, profile | 8081 |
| `orazaka-cli` | `apps/cli/` | Developer automation CLI with offline SQLite job queue | — |
| `orazaka-shared` | `sovkora-ui-kit (now krizaka-ui) orazaka-shared/` | Shared TypeScript types + Zod validation schemas | — |

> All client packs import types from `orazaka-shared` exclusively. Type duplication across packs is banned.

---

## Related Documentation
- [Developer Onboarding Guide](101.md)
- [Architecture Reference](_generated/ARCHITECTURE.md)
- [CLI Reference](_generated/CLI.md)
- [Auth & Security](AUTH.md)
- [Model Catalog](_generated/MODELS.md)
