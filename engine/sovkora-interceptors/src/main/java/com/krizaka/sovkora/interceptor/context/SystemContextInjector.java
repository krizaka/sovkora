package com.krizaka.sovkora.interceptor.context;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Order 2 — Single atomic service responsible for both resolving system/environment state and
 * injecting it into the timeline.
 */
class SystemContextInjector implements PromptContextInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(SystemContextInjector.class);

  SystemContextInjector() {
    // Zero-dependency atomic initialization
  }

  @Override
  public PromptContext intercept(PromptContext context) {
    logger.debug("Injecting system context signals directly.");
    var newSystemMetadata = new HashMap<>(context.systemMetadata());

    // Resolve system/environment state directly (previously resolved via providers)
    Map<String, Object> systemData =
        Map.of(
            "activeTools", "searchKnowledge, ttsGenerator, imageGenerator",
            "systemStatus", "OPERATIONAL",
            "activeTrends", "AI-agentic-flows, virtual-threads-concurrency");

    newSystemMetadata.putAll(systemData);

    // Propagate sessionId from router-injected userMetadata for cross-layer traceability
    Object sessionId = context.userMetadata().get(Context.SESSION_ID_KEY);
    if (sessionId != null) {
      newSystemMetadata.put("sessionId", sessionId);
    }

    return context.withSystemMetadata(Map.copyOf(newSystemMetadata));
  }
}
