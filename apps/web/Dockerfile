# Web client (Next.js) for www.orazaka.com — built by DigitalOcean App Platform.
# Source context: apps/web (source_dir: apps/web in sovkora-dev.app.yaml).
# Built standalone from its own pnpm-lock.yaml; no monorepo root is needed.
# dockerfile_path: apps/web/Dockerfile

FROM node:22-alpine AS builder
WORKDIR /app
# corepack ships with Node 22; pnpm is declared in allowBuilds of pnpm-workspace.yaml.
RUN corepack enable pnpm
# Dependency manifests first — Docker layer cache skips the install on subsequent builds
# when only source files changed.
COPY .npmrc package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
# Source after deps so a source-only change reuses the installed layer.
COPY . .
# Build-time env vars (APP_COMMIT, AUTH_*, APP_SEARCH_INDEXING) are injected by App Platform
# as BUILD_TIME / RUN_AND_BUILD_TIME envs before `docker build --build-arg` equivalents.
RUN pnpm run build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
# next start only needs node_modules (for the next binary), .next and public.
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/public ./public
COPY --from=builder /app/package.json ./
EXPOSE 8080
# --port 8080 matches http_port in sovkora-dev.app.yaml.
CMD ["node_modules/.bin/next", "start", "--port", "8080"]
