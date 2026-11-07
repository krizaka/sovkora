package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OperationNodeTest {

  private final NodeState state = new NodeState.Active();

  @Test
  void validConstruction() {
    // A node is an identity, a presentation context and a state since ADR-069 §5: the label, the
    // icon and the execution URI it carried were the registry's UI manifest, and that is gone.
    var node = new OperationNode("chat.image", "TOOLBAR", state);
    assertEquals("chat.image", node.id());
    assertEquals("TOOLBAR", node.presentationContext());
    assertEquals(state, node.state());
  }

  @Test
  void nullPresentationContext_defaultsToContextMenuPlus() {
    var node = new OperationNode("id", null, state);
    assertEquals("CONTEXT_MENU_PLUS", node.presentationContext());
  }

  @Test
  void blankPresentationContext_defaultsToContextMenuPlus() {
    var node = new OperationNode("id", "  ", state);
    assertEquals("CONTEXT_MENU_PLUS", node.presentationContext());
  }

  @Test
  void nullId_throws() {
    assertThrows(NullPointerException.class, () -> new OperationNode(null, null, state));
  }

  @Test
  void blankId_throws() {
    assertThrows(IllegalArgumentException.class, () -> new OperationNode("  ", null, state));
  }

  @Test
  void nullState_throws() {
    assertThrows(NullPointerException.class, () -> new OperationNode("id", null, null));
  }
}
