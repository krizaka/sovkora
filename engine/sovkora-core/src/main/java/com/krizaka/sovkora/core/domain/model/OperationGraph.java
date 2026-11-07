package com.krizaka.sovkora.core.domain.model;

import com.krizaka.sovkora.core.application.engine.GraphEngine;
import java.util.List;

/**
 * Immutable container for the compiled SDUI Operation Graph.
 *
 * <p>Holds the complete list of {@link OperationNode} capability nodes compiled by {@link
 * GraphEngine#compileGraph()}. This is the top-level data structure returned to clients (CLI, UI)
 * via the {@code operationGraph} GraphQL query.
 *
 * @param nodes The compiled list of operation nodes (defensively copied to unmodifiable).
 * @see GraphEngine
 * @see OperationNode
 */
public final record OperationGraph(List<OperationNode> nodes) {
  /** Compact constructor — defensively copies the list and handles null input. */
  public OperationGraph {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
  }
}
