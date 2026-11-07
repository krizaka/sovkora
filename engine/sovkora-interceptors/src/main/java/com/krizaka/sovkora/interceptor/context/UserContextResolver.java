package com.krizaka.sovkora.interceptor.context;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.infrastructure.support.SecurityContextUtil;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Order 1 — Extracts user profile attributes and RBAC security configurations from the Spring
 * Security context and enriches the {@link PromptContext}'s user metadata.
 *
 * @see SecurityContextUtil#extractSecurityMetadata()
 */
class UserContextResolver implements PromptContextInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(UserContextResolver.class);

  @Override
  public PromptContext intercept(PromptContext context) {
    logger.debug("Resolving user security context details...");
    var securityData = SecurityContextUtil.extractSecurityMetadata();
    if (securityData.isEmpty()) {
      return context;
    }
    var newUserMetadata = new HashMap<>(context.userMetadata());
    newUserMetadata.putAll(securityData);
    logger.debug(
        "Successfully enriched user metadata with security claims: {}", securityData.keySet());
    return context.withUserMetadata(Map.copyOf(newUserMetadata));
  }
}
