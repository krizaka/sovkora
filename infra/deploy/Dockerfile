# ==============================================================================
# Orazaka dev runtime — the JVM services of www.orazaka.com (and the document-validation worker) in ONE image (docs/DEPLOY_DEV.md)
# ------------------------------------------------------------------------------
# Built by DigitalOcean App Platform from this repository (dockerfile_path: infra/deploy/Dockerfile,
# context: the repository root). The platform is one repository per component, so the image clones
# every repository listed in orazaka.workspace.json at its default branch — exactly what CI does —
# and builds the eight services the dev environment runs, without tests (CI ran them).
# Redeploy with `doctl apps create-deployment <app> --force-rebuild`. The clone layer's cache follows the
# repositories' heads (the ADD lines of stage 1), so a change in any of them is cloned again.
# ==============================================================================

# ── 1. Sources: every repository of the workspace, shallow ───────────────────
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS sources
RUN apk add --no-cache git
WORKDIR /workspace
COPY . .
# The cache key of the clone below: each workspace repository's ref advertisement (its branch heads). A push to any of
# them changes a file here, so the clone runs again — `--force-rebuild` alone reused the cached clone and shipped
# yesterday's code (2026-10-16: a krizaka-users security fix did not reach dev). One line per repository of
# orazaka.workspace.json; scripts/test/dockerfile-clone-cache.test.mjs keeps them in step.
ADD https://github.com/krizaka/krizaka-build.git/info/refs?service=git-upload-pack /tmp/workspace-refs/krizaka-build
ADD https://github.com/krizaka/orazaka-build.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-build
ADD https://github.com/krizaka/orazaka-contracts.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-contracts
ADD https://github.com/krizaka/orazaka-studio.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-studio
ADD https://github.com/krizaka/orazaka-ai-engine.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-ai-engine
ADD https://github.com/krizaka/orazaka-conversation-service.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-conversation-service
ADD https://github.com/krizaka/orazaka-job-service.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-job-service
ADD https://github.com/krizaka/orazaka-knowledge-service.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-knowledge-service
ADD https://github.com/krizaka/orazaka-automation-service.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-automation-service
ADD https://github.com/krizaka/orazaka-edge.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-edge
ADD https://github.com/krizaka/orazaka-worker-media.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-worker-media
ADD https://github.com/krizaka/orazaka-ui-kit.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-ui-kit
ADD https://github.com/krizaka/orazaka-web-client.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-web-client
ADD https://github.com/krizaka/orazaka-mobile-client.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-mobile-client
ADD https://github.com/krizaka/orazaka-cli.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-cli
ADD https://github.com/krizaka/orazaka-packs.git/info/refs?service=git-upload-pack /tmp/workspace-refs/orazaka-packs
# The apply's stamp (CORE_REBUILD, a BUILD_TIME variable of the core component) is part of the clone's cache key too: the builder
# kept the cached clone (and its packs and jars) although a repository had moved — the ADD of a remote ref file is cached by URL.
ARG CORE_REBUILD=0
RUN echo "sources ${CORE_REBUILD}" > /tmp/sources-stamp
RUN node scripts/workspace.mjs clone --depth 1
# The Maven settings of the build: the GitHub Packages repositories of the Krizaka building blocks (ADR-019, krizaka-internal#1879).
# Written by the org's own generator (krizaka/.github, pinned), WITHOUT a credential: the server reads ${env.MAVEN_GITHUB_PACKAGES_TOKEN},
# so nothing secret is in a layer. The token itself arrives as a build secret in stage 2.
ARG GITHUB_ORG_SHA=99281c3f4b46dbe4c645dff053b4bfb387a1403d
RUN mkdir /org && cd /org && git init -q && git fetch -q --depth 1 https://github.com/krizaka/.github.git "$GITHUB_ORG_SHA" && git checkout -q FETCH_HEAD \
 && node scripts/maven-github-packages-settings.mjs --settings /m2/settings.xml --repositories "krizaka-build" --username krizaka-agents \
 && ! grep -q -E '<password>[^$]' /m2/settings.xml

# ── 2. Build the eight services and what they depend on ──────────────────────
FROM eclipse-temurin:21-jdk@sha256:f9bf7e9fe2f9bc4657537c1a7e20c768f0c3bafca41598263be4714783dd62eb AS build
WORKDIR /workspace
COPY --from=sources /workspace /workspace
COPY --from=sources /m2/settings.xml /root/.m2/settings.xml
# The cloned repositories change under an unchanged Dockerfile: App Platform's layer cache then reuses yesterday's
# build (a merged route or endpoint never reaches the image). CORE_REBUILD is a BUILD_TIME variable of the core
# component (deploy/orazaka-dev.app.yaml); changing its value — `scripts/dev-deploy.mjs` sets the date — busts it.
ARG CORE_REBUILD=0
# The read credential of GitHub Packages (read:packages), a BUILD SECRET: a BUILD_TIME secret of the App Platform component
# (deploy/orazaka-dev.app.yaml) or `docker build --build-arg MAVEN_GITHUB_PACKAGES_TOKEN` (the value from the environment,
# never typed). This stage is not shipped (stage 3 copies /out only), and the settings above never held the value.
ARG MAVEN_GITHUB_PACKAGES_TOKEN
RUN test -n "$MAVEN_GITHUB_PACKAGES_TOKEN" || { echo "MAVEN_GITHUB_PACKAGES_TOKEN is not set: the Krizaka building blocks are on GitHub Packages (docs/DEPLOY_DEV.md)" >&2; exit 1; }
RUN echo "core build ${CORE_REBUILD}" \
 && \
    ./mvnw -B -ntp -q install \
      -DskipTests -DskipITs=true -Dspotless.check.skip=true -Dexec.skip=true \
      -Dskip.npm=true -Dskip.installnodenpm=true \
      -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -Dgpg.skip=true -Dkrizaka.quality.phase=none \
      -pl services/edge/sovkora-edge-service,krizaka/krizaka-build/krizaka-users/krizaka-users-service,krizaka/krizaka-build/krizaka-billing/krizaka-billing-service,services/conversation/sovkora-conversation-service,services/studio/sovkora-studio-service,services/job/sovkora-job-service,services/knowledge/sovkora-knowledge-service,services/automation/sovkora-automation-service \
      -am \
 && mkdir -p /out \
 && cp services/edge/sovkora-edge-service/target/sovkora-edge-*[0-9T].jar /out/edge.jar \
 && cp krizaka/krizaka-build/krizaka-users/krizaka-users-service/target/krizaka-users-service-*[0-9T].jar /out/users.jar \
 && cp krizaka/krizaka-build/krizaka-billing/krizaka-billing-service/target/krizaka-billing-service-*[0-9T].jar /out/billing.jar \
 && cp services/conversation/sovkora-conversation-service/target/sovkora-conversation-service-*[0-9T].jar /out/conversation.jar \
 && cp services/studio/sovkora-studio-service/target/sovkora-studio-service-*[0-9T].jar /out/studio.jar \
 && cp services/job/sovkora-job-service/target/sovkora-job-service-*[0-9T].jar /out/job.jar \
 && cp services/knowledge/sovkora-knowledge-service/target/sovkora-knowledge-service-*[0-9T].jar /out/knowledge.jar \
 && cp services/automation/sovkora-automation-service/target/sovkora-automation-service-*[0-9T].jar /out/automation.jar \
 && cp -r orazaka-packs /out/orazaka-packs \
 && cp -r workers/media /out/orazaka-worker-media \
 && find /workspace -type d -name target -prune -exec rm -rf {} + \
 && rm -rf /root/.m2/repository
# ^ the jars are in /out: the builder snapshots this layer, and with the eighth service its size (every target/ and the
#   local Maven repository) made the snapshot run out of memory (build exit -1). Nothing after this stage reads them.

# ── 3. Runtime: a JRE, the jars, one supervisor ──────────────────────────────
FROM eclipse-temurin:21-jre@sha256:ccbad9e7cb84c27092b12c40a4143e5733e050db9c3784d84f632dcf423fb2ef
# The document-validation worker (orazaka-packs, Tier-W, Python): it drains job.validation.* for the document
# Studios. Its venv is built here with the pack's pinned dependencies; run-services.sh starts it beside the JVMs.
RUN export DEBIAN_FRONTEND=noninteractive \
 && apt-get update \
 && apt-get install -y --no-install-recommends python3 python3-venv \
 && rm -rf /var/lib/apt/lists/* \
 && python3 -m venv /opt/validation-venv \
 && /opt/validation-venv/bin/pip install --no-cache-dir pika==1.4.4 pyyaml==6.0.3 pypdf==6.20.0 cryptography==46.0.3 pillow==11.3.0 psutil==7.1.3
RUN groupadd --system orazaka && useradd --system --gid orazaka --create-home orazaka
WORKDIR /app
COPY --from=build /out/ /app/
COPY infra/deploy/run-services.sh /app/run-services.sh
RUN chmod 0755 /app/run-services.sh && mkdir -p /app/var/logs /app/var/orazaka-uploads && chown -R orazaka:orazaka /app
USER orazaka
EXPOSE 8088
ENTRYPOINT ["/app/run-services.sh"]
