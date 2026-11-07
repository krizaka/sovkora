package com.krizaka.sovkora.tools.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for MCP tools that perform write operations.
 *
 * <p>Methods annotated with {@code @McpWriteTool} are intercepted by the {@code SandboxAspect},
 * which diverts all write operations into an in-memory Jimfs FileSystem isolated by {@code jobId}.
 * The sandbox must be explicitly committed (flushing bytes to real storage) or rolled back
 * (evicting context).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpWriteTool {

  /** Description of the write operation for audit logging. */
  String value() default "";
}
