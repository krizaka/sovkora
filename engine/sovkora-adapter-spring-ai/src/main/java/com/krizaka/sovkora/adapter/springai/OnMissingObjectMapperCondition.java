package com.krizaka.sovkora.adapter.springai;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import tools.jackson.databind.ObjectMapper;

/**
 * Condition that matches when no Jackson {@link ObjectMapper} bean is defined in the application
 * context.
 *
 * <p>Vanilla Spring replacement for {@code @ConditionalOnMissingBean}, which lives in {@code
 * org.springframework.boot.autoconfigure} and is therefore banned in {@code sovkora-core} by the
 * layer-boundary rule (core must not import Spring Boot starter auto-configuration). Mirrors {@link
 * OnMissingVectorStoreCondition}.
 */
public class OnMissingObjectMapperCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    if (context.getBeanFactory() == null) {
      return true;
    }
    return context.getBeanFactory().getBeanNamesForType(ObjectMapper.class).length == 0;
  }
}
