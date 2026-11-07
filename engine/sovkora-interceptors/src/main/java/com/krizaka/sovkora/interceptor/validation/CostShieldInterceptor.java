package com.krizaka.sovkora.interceptor.validation;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Order 9 — Security compliance interceptor and hardware telemetry monitor.
 *
 * <p>Monitors M1 unified memory usage and enriches the {@link PromptContext} system metadata with
 * hardware telemetry signals. This interceptor is NOT AI-dependent — it performs local hardware
 * queries only.
 *
 * <p>The security governance kill-switch ({@code orazaka.security.disable-ai=true}) is enforced at
 * the {@code DynamicPipelineExecutor} level, not here. This interceptor always executes.
 *
 * @since 2026.1.0
 */
public class CostShieldInterceptor implements PromptContextInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(CostShieldInterceptor.class);

  /** Memory usage threshold above which tasks should be offloaded to cloud. */
  private static final double MEMORY_THRESHOLD_PERCENT = 85.0;

  /** Cloud provider to recommend when local resources are constrained. */
  private static final String CLOUD_FALLBACK_PROVIDER = "openai";

  @Override
  public PromptContext intercept(PromptContext context) {
    double memoryUsage = getUnifiedMemoryUsagePercent();
    boolean shouldOffload = memoryUsage > MEMORY_THRESHOLD_PERCENT;

    var newSystemMetadata = new HashMap<>(context.systemMetadata());
    newSystemMetadata.put("memoryUsagePercent", memoryUsage);
    newSystemMetadata.put("costShieldActive", shouldOffload);

    if (shouldOffload) {
      newSystemMetadata.put("recommendedProvider", CLOUD_FALLBACK_PROVIDER);
      if (logger.isWarnEnabled()) {
        logger.warn(
            "CostShield ACTIVE — M1 unified memory at {}% (threshold: {}%). "
                + "Recommending cloud offload to '{}'.",
            String.format("%.1f", memoryUsage), MEMORY_THRESHOLD_PERCENT, CLOUD_FALLBACK_PROVIDER);
      }
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "CostShield passive — M1 unified memory at {}% (threshold: {}%).",
            String.format("%.1f", memoryUsage), MEMORY_THRESHOLD_PERCENT);
      }
    }

    return context.withSystemMetadata(Map.copyOf(newSystemMetadata));
  }

  /**
   * Queries macOS unified memory usage via {@code vm_stat} and calculates utilization percentage.
   * Falls back to 0.0 on non-macOS platforms or command failures.
   */
  private double getUnifiedMemoryUsagePercent() {
    try {
      ProcessBuilder pb = new ProcessBuilder("/usr/bin/vm_stat");
      pb.redirectErrorStream(true);
      Process process = pb.start();

      long freePages = 0;
      long activePages = 0;
      long inactivePages = 0;
      long wiredPages = 0;
      long compressedPages = 0;

      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.contains("Pages free")) {
            freePages = extractPageCount(line);
          } else if (line.contains("Pages active")) {
            activePages = extractPageCount(line);
          } else if (line.contains("Pages inactive")) {
            inactivePages = extractPageCount(line);
          } else if (line.contains("Pages wired")) {
            wiredPages = extractPageCount(line);
          } else if (line.contains("Pages occupied by compressor")) {
            compressedPages = extractPageCount(line);
          }
        }
      }

      process.waitFor();

      long totalPages = freePages + activePages + inactivePages + wiredPages + compressedPages;
      if (totalPages == 0) {
        return 0.0;
      }

      long usedPages = activePages + wiredPages + compressedPages;
      return (double) usedPages / totalPages * 100.0;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.debug("vm_stat interrupted — CostShield will remain passive.", e);
      return 0.0;
    } catch (Exception e) {
      logger.debug("Could not read vm_stat — CostShield will remain passive.", e);
      return 0.0;
    }
  }

  /** Extracts the numeric page count from a vm_stat output line. */
  private long extractPageCount(String line) {
    String[] parts = line.split(":");
    if (parts.length < 2) {
      return 0;
    }
    String value = parts[1].trim().replace(".", "");
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
