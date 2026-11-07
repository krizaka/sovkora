package com.krizaka.sovkora.core.domain.model;

import java.util.Objects;

/**
 * Standardized polymorphic contract for all AI capability payloads.
 *
 * <p>All implementors (ChatRequest, ImageRequest, AudioRequest) must call {@link
 * #requireValidPrompt(String)} in their compact constructors to enforce the non-null invariant.
 */
public interface AiRequest {

  /**
   * Returns the main input string/prompt for the request.
   *
   * @return The input string.
   */
  String prompt();

  /**
   * Returns the security and preferences context of the execution.
   *
   * @return The execution context.
   */
  Context context();

  /**
   * Centralized validation for all AI request records. Java records cannot extend abstract classes,
   * so this static method DRYs the common invariant checks for both shared fields.
   *
   * @param prompt The prompt to validate.
   * @param context The execution context to validate.
   * @throws NullPointerException if prompt or context is null.
   * @throws IllegalArgumentException if prompt is blank.
   */
  static void requireValid(String prompt, Context context) {
    Objects.requireNonNull(prompt, "prompt must not be null");
    if (prompt.isBlank()) {
      throw new IllegalArgumentException("prompt must not be blank");
    }
    Objects.requireNonNull(context, "context must not be null");
  }
}
