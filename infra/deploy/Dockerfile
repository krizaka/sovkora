# ==============================================================================
# Sovkora dev runtime — the JVM services of dev.sovkora.com (and the document-validation worker) in ONE image (docs/DEPLOY_DEV.md)
# ------------------------------------------------------------------------------
# Built by DigitalOcean App Platform from this repository (dockerfile_path: infra/deploy/Dockerfile, context: the
# repository root). Sovkora is one repository (ADR-029): the image builds the services from this tree, without tests (CI
# ran them). The users and billing services it runs beside Sovkora's are krizaka-java's: they are built from krizaka-java
# at the release sovkora-parent imports (krizaka.version), the one repository this image still fetches.
# Redeploy with `doctl apps create-deployment <app> --force-rebuild`.
# ==============================================================================

# ── 1. krizaka-java at the release Sovkora is built on ───────────────────────
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS krizaka
RUN apk add --no-cache git
WORKDIR /src
COPY build/sovkora-parent/pom.xml /tmp/sovkora-parent.pom
# The release is read from the parent, never typed here: the image builds the users and billing services Sovkora's BOM names.
RUN version=$(sed -n 's:.*<krizaka.version>\(.*\)</krizaka.version>.*:\1:p' /tmp/sovkora-parent.pom) \
 && test -n "$version" \
 && git -c advice.detachedHead=false clone -q --depth 1 --branch "v$version" https://github.com/krizaka/krizaka-java.git krizaka-java

# ── 2. Build the eight services ───────────────────────────────────────────────
FROM eclipse-temurin:21-jdk@sha256:f9bf7e9fe2f9bc4657537c1a7e20c768f0c3bafca41598263be4714783dd62eb AS build
WORKDIR /workspace
COPY . .
COPY --from=krizaka /src/krizaka-java /krizaka-java
# The cloned tree changes under an unchanged Dockerfile: App Platform's layer cache could reuse yesterday's build.
# CORE_REBUILD is a BUILD_TIME variable of the core component; changing its value (scripts/dev-deploy.mjs sets the date) busts it.
ARG CORE_REBUILD=0
RUN echo "core build ${CORE_REBUILD}" \
 && cd /krizaka-java \
 && ./mvnw -B -ntp -q install \
      -DskipTests -DskipITs=true -Dspotless.check.skip=true -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -Dgpg.skip=true \
      -Dkrizaka.quality.phase=none -pl krizaka-users/krizaka-users-service,krizaka-billing/krizaka-billing-service -am \
 && cd /workspace \
 && ./mvnw -B -ntp -q install \
      -DskipTests -DskipITs=true -Dspotless.check.skip=true \
      -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -Dgpg.skip=true -Dkrizaka.quality.phase=none \
      -pl services/edge/sovkora-edge-service,services/conversation/sovkora-conversation-service,services/studio/sovkora-studio-service,services/job/sovkora-job-service,services/knowledge/sovkora-knowledge-service,services/automation/sovkora-automation-service \
      -am \
 && mkdir -p /out \
 && cp services/edge/sovkora-edge-service/target/sovkora-edge-service-*[0-9T].jar /out/edge.jar \
 && cp /krizaka-java/krizaka-users/krizaka-users-service/target/krizaka-users-service-*[0-9T].jar /out/users.jar \
 && cp /krizaka-java/krizaka-billing/krizaka-billing-service/target/krizaka-billing-service-*[0-9T].jar /out/billing.jar \
 && cp services/conversation/sovkora-conversation-service/target/sovkora-conversation-service-*[0-9T].jar /out/conversation.jar \
 && cp services/studio/sovkora-studio-service/target/sovkora-studio-service-*[0-9T].jar /out/studio.jar \
 && cp services/job/sovkora-job-service/target/sovkora-job-service-*[0-9T].jar /out/job.jar \
 && cp services/knowledge/sovkora-knowledge-service/target/sovkora-knowledge-service-*[0-9T].jar /out/knowledge.jar \
 && cp services/automation/sovkora-automation-service/target/sovkora-automation-service-*[0-9T].jar /out/automation.jar \
 && cp -r packs /out/orazaka-packs \
 && cp -r workers/media /out/orazaka-worker-media \
 && find /workspace /krizaka-java -type d -name target -prune -exec rm -rf {} + \
 && rm -rf /root/.m2/repository
# ^ the jars are in /out: the builder snapshots this layer, and with the eighth service its size (every target/ and the
#   local Maven repository) made the snapshot run out of memory (build exit -1). Nothing after this stage reads them.
# The runtime folders keep their names (/app/orazaka-packs, ORAZAKA_PACKS_SOURCES on dev): runtime names move in
# krizaka-internal#2872, with the dev environment's keys.

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
