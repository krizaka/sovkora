# Web client (Next.js) for www.orazaka.com — built by DigitalOcean App Platform.
# Source context: apps/web (source_dir: apps/web in sovkora-dev.app.yaml).
# Built standalone from its own pnpm-lock.yaml; no monorepo root is needed.
# dockerfile_path: apps/web/Dockerfile

# glibc image: @ast-grep/cli ships no musl binary, so its postinstall fails on Alpine.
FROM node:22-slim AS builder
WORKDIR /app
# corepack ships with Node 22; pnpm is declared in allowBuilds of pnpm-workspace.yaml.
RUN corepack enable pnpm
# Dependency manifests first — Docker layer cache skips the install on subsequent builds
# when only source files changed.
COPY .npmrc package.json pnpm-lock.yaml pnpm-workspace.yaml ./
# The postinstall runs scripts/link-fonts.mjs, so the script must be there before the install.
COPY scripts ./scripts
RUN pnpm install --frozen-lockfile
# Source after deps so a source-only change reuses the installed layer.
COPY . .
# App Platform passes build-time env vars as docker build args: each must be declared to reach the build.
ARG APP_COMMIT
ARG AUTH_LOGIN_ENABLED
ARG APP_SEARCH_INDEXING
ARG AUTH_SIGNUP_ENABLED
ENV APP_COMMIT=$APP_COMMIT AUTH_LOGIN_ENABLED=$AUTH_LOGIN_ENABLED APP_SEARCH_INDEXING=$APP_SEARCH_INDEXING AUTH_SIGNUP_ENABLED=$AUTH_SIGNUP_ENABLED
RUN pnpm run build

FROM node:22-slim
WORKDIR /app
ENV NODE_ENV=production
# The whole app: next.config.ts imports from src/ and the pages read content/ from disk at run time.
COPY --from=builder /app ./
EXPOSE 8080
# --port 8080 matches http_port in sovkora-dev.app.yaml.
CMD ["node_modules/.bin/next", "start", "--port", "8080"]
