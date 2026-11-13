package com.krizaka.sovkora.engine.interceptors.governance;

import com.krizaka.sovkora.engine.core.domain.model.Context;
import java.util.Map;

/**
 * What a monthly provider-spend cap is counted against: one environment and one product key.
 *
 * <p>The product key is <b>declared</b> by the producer of the turn under {@link
 * #PRODUCT_DECLARATION} (ADR-050, AGENTS §12); a turn that declares none is counted in the explicit
 * {@link #UNATTRIBUTED} scope, which has a cap row of its own, rather than in a product it happened
 * to look like.
 *
 * @param environment the deployment the spend belongs to ({@code local}, {@code dev}, {@code prod})
 * @param productKey the product the turn was served for
 */
public record SpendScope(String environment, String productKey) {

  /** The context key under which a producer declares the product a turn is served for. */
  public static final String PRODUCT_DECLARATION = Context.PLATFORM_NAMESPACE + "spend.product";

  /** The scope of a turn whose producer declared no product. */
  public static final String UNATTRIBUTED = "unattributed";

  public SpendScope {
    if (environment == null || environment.isBlank()) {
      throw new IllegalArgumentException("SpendScope requires a non-blank environment");
    }
    if (productKey == null || productKey.isBlank()) {
      throw new IllegalArgumentException("SpendScope requires a non-blank productKey");
    }
  }

  /**
   * The scope of a turn.
   *
   * @param environment the environment this deployment declares
   * @param declarations the turn's platform declarations (its context metadata)
   * @return the declared product's scope, or the {@link #UNATTRIBUTED} one
   */
  public static SpendScope of(String environment, Map<String, Object> declarations) {
    Object declared = declarations.get(PRODUCT_DECLARATION);
    String product = declared instanceof String s && !s.isBlank() ? s : UNATTRIBUTED;
    return new SpendScope(environment, product);
  }

  /** The scope as a metric tag and a log token: environment and product, never an actor. */
  public String label() {
    return environment + "/" + productKey;
  }
}
