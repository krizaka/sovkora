package com.krizaka.sovkora.core.application.engine;

import com.krizaka.sovkora.core.domain.model.CapabilityDescriptor;
import com.krizaka.sovkora.core.domain.model.NodeState;
import com.krizaka.sovkora.core.domain.model.NodeState.Active;
import com.krizaka.sovkora.core.domain.model.NodeState.Invisible;
import com.krizaka.sovkora.core.domain.model.NodeState.Locked;
import com.krizaka.sovkora.core.domain.model.OperationGraph;
import com.krizaka.sovkora.core.domain.model.OperationNode;
import com.krizaka.sovkora.core.domain.ports.outbound.AdminRegistry;
import com.krizaka.sovkora.core.domain.ports.outbound.CapabilityProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.InfrastructureStatusProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Short-circuit compilation engine resolving database capabilities and runtime lock registries. */
public class GraphEngine {

  private final CapabilityProvider capabilityProvider;
  private final AdminRegistry adminRegistry;
  private final InfrastructureStatusProvider prober;
  private final ModelCatalogProvider modelCatalogProvider;

  /**
   * Constructs the engine.
   *
   * @param capabilityProvider Database-backed source of capability descriptors (blueprint + enabled
   *     state). Nullable — a null provider yields an empty graph.
   * @param adminRegistry Dynamic runtime lock store.
   * @param prober Asynchronous infrastructure status prober.
   * @param modelCatalogProvider The model catalog provider to resolve active models dynamically.
   */
  public GraphEngine(
      CapabilityProvider capabilityProvider,
      AdminRegistry adminRegistry,
      InfrastructureStatusProvider prober,
      ModelCatalogProvider modelCatalogProvider) {
    this.capabilityProvider = capabilityProvider;
    this.adminRegistry =
        Objects.requireNonNull(adminRegistry, "Admin lock registry cannot be null");
    this.prober = Objects.requireNonNull(prober, "Infrastructure prober cannot be null");
    this.modelCatalogProvider = modelCatalogProvider;
  }

  /** Cache TTL in seconds. The graph changes rarely (capability toggles, model registration). */
  private static final long CACHE_TTL_SECONDS = 30;

  /** Immutable cache entry holding both graph and timestamp atomically. */
  private record CacheEntry(OperationGraph graph, Instant timestamp) {}

  /** Thread-safe cached graph result to avoid redundant computation on frequent UI polls. */
  private final AtomicReference<CacheEntry> cache =
      new AtomicReference<>(new CacheEntry(null, Instant.EPOCH));

  /**
   * Compiles database capabilities with dynamic locks and infrastructure probes.
   *
   * <p>Short-circuits evaluation: If a capability is disabled in the database, dynamic data checks
   * are bypassed entirely and the node evaluates to {@link Invisible}.
   *
   * @return A compiled {@link OperationGraph} matrix instance.
   */
  public OperationGraph compileGraph() {
    CacheEntry entry = cache.get();
    if (entry.graph() != null
        && Instant.now().isBefore(entry.timestamp().plusSeconds(CACHE_TTL_SECONDS))) {
      return entry.graph();
    }
    OperationGraph result = doCompileGraph();
    cache.set(new CacheEntry(result, Instant.now()));
    return result;
  }

  /**
   * Invalidates the cached graph, forcing recompilation on the next call. Should be invoked when
   * capabilities are toggled or models change.
   */
  public void invalidateCache() {
    cache.set(new CacheEntry(null, Instant.EPOCH));
  }

  /** Internal compilation logic, extracted for caching wrapper. */
  private OperationGraph doCompileGraph() {
    if (capabilityProvider == null) {
      return new OperationGraph(List.of());
    }
    List<OperationNode> nodes = new ArrayList<>();
    final String activeModel =
        modelCatalogProvider != null
            ? modelCatalogProvider.getActiveChatModel().orElse(null)
            : null;

    for (CapabilityDescriptor capability : capabilityProvider.findAll()) {
      // Every capability is a node now. The skip that was here kept broker-routed capabilities out
      // because they had no endpoint for an interface to offer — a distinction that stopped being
      // one when door 1 closed: nothing is clicked, everything is dispatched by a run (ADR-068),
      // and what this graph answers is "which capabilities can this deployment currently run".
      NodeState state =
          capability.enabled()
              ? resolveNodeState(capability.featureKey(), activeModel)
              : new Invisible();
      nodes.add(buildNode(capability, state));
    }

    return new OperationGraph(nodes);
  }

  /** Resolves the runtime node state for an enabled capability. */
  private NodeState resolveNodeState(String id, String activeModel) {
    if (activeModel == null) {
      return new Locked(
          "Capability missing: No active Ollama chat model found in registry",
          LocalDateTime.now(ZoneOffset.UTC));
    }
    if ("orazaka.core.media.video".equals(id)) {
      return resolveMediaState(id);
    }
    if ("orazaka.core.media.image".equals(id)) {
      return resolveImageState(id);
    }
    return resolveAdminLockState(id);
  }

  /** Resolves state for the image media feature, gated by its own (Stable Diffusion) probe. */
  private NodeState resolveImageState(String id) {
    boolean imageOnline = prober != null && prober.isImageEngineOnline();
    if (!imageOnline) {
      int iPort = prober != null ? prober.getImageProbePort() : 8086;
      return new Locked("Image engine offline on port " + iPort, LocalDateTime.now(ZoneOffset.UTC));
    }
    return resolveAdminLockState(id);
  }

  /** Resolves state for the video media feature, checking infrastructure probes. */
  private NodeState resolveMediaState(String id) {
    boolean videoOnline = prober != null && prober.isVideoEngineOnline();
    boolean imageOnline = prober != null && prober.isImageEngineOnline();
    if (!videoOnline && !imageOnline) {
      int vPort = prober != null ? prober.getVideoProbePort() : 8188;
      int iPort = prober != null ? prober.getImageProbePort() : 8085;
      return new Locked(
          "Video engine offline on port "
              + vPort
              + " and fallback image engine offline on port "
              + iPort,
          LocalDateTime.now(ZoneOffset.UTC));
    }
    return resolveAdminLockState(id);
  }

  /** Resolves state from admin lock registry, defaulting to Active. */
  private NodeState resolveAdminLockState(String id) {
    return adminRegistry
        .getLock(id)
        .<NodeState>map(lock -> new Locked(lock.reason(), lock.lockedAt()))
        .orElse(new Active());
  }

  /**
   * Builds an OperationNode from a capability descriptor and resolved state.
   *
   * <p>A node is an identity and a state. The label, the icon and the {@code TargetExecutionUri}
   * that were here are gone with the columns they read (ADR-069 §5) — a display name belongs to the
   * pack that ships the Studio, and the endpoints this graph advertised were deleted with door 1.
   */
  private static OperationNode buildNode(CapabilityDescriptor capability, NodeState state) {
    return new OperationNode(capability.featureKey(), "CONTEXT_MENU_PLUS", state);
  }
}
