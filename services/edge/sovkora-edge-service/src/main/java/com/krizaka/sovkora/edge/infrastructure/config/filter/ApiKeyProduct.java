package com.krizaka.sovkora.edge.infrastructure.config.filter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The product an {@code oz_} key serves, declared when the key is minted (krizaka-internal#3368).
 *
 * <p><b>Declared, not inferred</b> (AGENTS.md §12): a key states its product with one scope, {@code
 * product:<key>}, exactly as it states its environment ({@link ApiKeyEnvironment}). The edge hands
 * it to the backends in {@link #HEADER}, a header of the reserved {@code x-sovkora-} family that
 * the proxy strips from every client request, so a caller can never name its own product. A key
 * that declares none is counted as unattributed, which has a cap row of its own. More than one
 * product, or a malformed one, is refused (fail closed). It opens no route ({@link ApiKeyScopes}
 * skips it).
 *
 * @param key the product key, lower-case letters, digits and hyphens
 */
public record ApiKeyProduct(String key) {

  /** The header the edge sets for the backends; the one place its name is written. */
  public static final String HEADER = "X-Sovkora-Product";

  /** The request attribute the exchange filter sets and the proxy reads. */
  public static final String ATTRIBUTE = ApiKeyProduct.class.getName() + ".key";

  private static final String SCOPE_PREFIX = "product:";
  private static final Pattern KEY_SHAPE = Pattern.compile("[a-z][a-z0-9-]{0,62}");

  public ApiKeyProduct {
    if (key == null || !KEY_SHAPE.matcher(key).matches()) {
      throw new IllegalArgumentException("a product key is lower-case letters, digits and hyphens");
    }
  }

  /** {@code true} for the scope that states a key's product, which is not a route scope. */
  static boolean isProductScope(String scope) {
    return scope.startsWith(SCOPE_PREFIX);
  }

  /**
   * The product a key with these scopes declares.
   *
   * @param scopes the key's scopes, possibly {@code null}
   * @return the product, or empty when the key declares none
   * @throws IllegalArgumentException when it declares more than one, or a malformed one
   */
  static Optional<ApiKeyProduct> declaredBy(Collection<String> scopes) {
    List<String> declared =
        scopes == null ? List.of() : scopes.stream().filter(ApiKeyProduct::isProductScope).toList();
    if (declared.size() > 1) {
      throw new IllegalArgumentException("a key declares one product at most");
    }
    return declared.stream()
        .findFirst()
        .map(scope -> new ApiKeyProduct(scope.substring(SCOPE_PREFIX.length())));
  }
}
