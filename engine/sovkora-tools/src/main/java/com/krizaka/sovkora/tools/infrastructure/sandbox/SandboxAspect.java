package com.krizaka.sovkora.tools.infrastructure.sandbox;

import com.krizaka.sovkora.tools.api.McpWriteTool;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that intercepts tools annotated with {@link McpWriteTool}.
 *
 * <p>Diverts all write operations into an in-memory Jimfs FileSystem isolated by {@code jobId}. The
 * jobId is resolved from the first String argument of the intercepted method.
 */
@Aspect
@Component
class SandboxAspect {

  private static final Logger log = LoggerFactory.getLogger(SandboxAspect.class);

  private final SandboxLifecycle sandboxLifecycle;

  SandboxAspect(SandboxLifecycle sandboxLifecycle) {
    this.sandboxLifecycle = sandboxLifecycle;
  }

  /** Intercept {@code @McpWriteTool} annotated methods and ensure sandbox context is active. */
  @Around("@annotation(mcpWriteTool)")
  Object aroundWriteTool(ProceedingJoinPoint joinPoint, McpWriteTool mcpWriteTool)
      throws Throwable {
    String jobId = resolveJobId(joinPoint);
    if (jobId == null) {
      log.warn(
          "No jobId resolved for @McpWriteTool method {}. Executing without sandbox.",
          joinPoint.getSignature().toShortString());
      return joinPoint.proceed();
    }

    log.debug("Sandboxing write operation for jobId={}: {}", jobId, mcpWriteTool.value());
    sandboxLifecycle.getSandboxRoot(jobId);

    try {
      return joinPoint.proceed();
    } catch (SandboxCapExceededException e) {
      log.error("Sandbox cap exceeded for jobId={}: {}", jobId, e.getMessage());
      sandboxLifecycle.rollback(jobId);
      throw e;
    }
  }

  /** Resolve jobId from the first String argument of the intercepted method. */
  private static String resolveJobId(ProceedingJoinPoint joinPoint) {
    for (Object arg : joinPoint.getArgs()) {
      if (arg instanceof String s && !s.isBlank()) {
        return s;
      }
    }
    return null;
  }
}
