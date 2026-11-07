package com.krizaka.sovkora.core.infrastructure.support;

import com.krizaka.sovkora.core.domain.model.Context;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reflective security context utility to extract authentication details dynamically.
 *
 * <p>Avoids tight coupling with Spring Security core at compile-time to maintain clean
 * architectural decoupling (No Starter Leaks).
 */
public final class SecurityContextUtil {

  private static final Logger logger = LoggerFactory.getLogger(SecurityContextUtil.class);

  private SecurityContextUtil() {}

  /**
   * Reflectively pulls principal attributes from the active SecurityContextHolder thread-local
   * context.
   *
   * @return Map of credentials details or an empty map if unauthenticated.
   */
  public static Map<String, Object> extractSecurityMetadata() {
    Map<String, Object> metadata = new HashMap<>();
    try {
      Class<?> holderClass =
          Class.forName("org.springframework.security.core.context.SecurityContextHolder");
      Object context = holderClass.getMethod("getContext").invoke(null);
      Object auth = context.getClass().getMethod("getAuthentication").invoke(context);
      if (auth != null) {
        Object principal = auth.getClass().getMethod("getPrincipal").invoke(auth);
        if (principal != null) {
          metadata.put("principalClass", principal.getClass().getName());
          safeInvoke(principal, "id", metadata, "userId");
          safeInvoke(principal, "username", metadata, "username");
          safeInvoke(principal, "email", metadata, "email");
          safeInvoke(principal, "rateLimitTier", metadata, "rateLimitTier");
          extractPreferences(principal, metadata);
        }
      }
    } catch (Exception ignored) {
      logger.trace(
          "Failed reflective call to retrieve SecurityContext or Authentication principal",
          ignored);
    }
    return metadata;
  }

  private static void extractPreferences(Object principal, Map<String, Object> metadata) {
    try {
      Method prefMethod = principal.getClass().getMethod("preferences");
      Object prefs = prefMethod.invoke(principal);
      if (prefs instanceof Map<?, ?> map) {
        Map<String, Object> written = new HashMap<>();
        map.entrySet().stream()
            .filter(entry -> entry.getKey() != null)
            .forEach(entry -> written.put(entry.getKey().toString(), entry.getValue()));
        metadata.putAll(Context.userPreferences(written));
      }
    } catch (Exception ignored) {
      logger.trace("Failed reflective call to retrieve user preferences map", ignored);
    }
  }

  private static void safeInvoke(
      Object obj, String methodName, Map<String, Object> target, String key) {
    try {
      Method method = obj.getClass().getMethod(methodName);
      Object val = method.invoke(obj);
      if (val != null) {
        target.put(key, val.toString());
      }
    } catch (Exception ignored) {
      logger.trace("Failed reflective invocation of method '{}' on principal", methodName, ignored);
    }
  }
}
