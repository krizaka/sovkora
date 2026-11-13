package com.krizaka.sovkora.edge.infrastructure.config.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The product a key declares (a {@code product:} scope), the one a run is counted under. */
class ApiKeyProductTest {

  @Test
  @DisplayName("[3368] a key declares its product with one product: scope")
  void declaredByScope() {
    assertThat(ApiKeyProduct.declaredBy(List.of("env:prod", "ai:embed", "product:verikora")))
        .contains(new ApiKeyProduct("verikora"));
  }

  @Test
  @DisplayName("[3368] a key that declares no product has none: the turn stays unattributed")
  void noneDeclared() {
    assertThat(ApiKeyProduct.declaredBy(List.of("env:prod"))).isEqualTo(Optional.empty());
    assertThat(ApiKeyProduct.declaredBy(null)).isEqualTo(Optional.empty());
  }

  @Test
  @DisplayName("[3368] two products, or a malformed one, is refused (fail closed)")
  void ambiguousIsRefused() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> ApiKeyProduct.declaredBy(List.of("product:verikora", "product:folioviv")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ApiKeyProduct.declaredBy(List.of("product:Veri kora")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ApiKeyProduct.declaredBy(List.of("product:")));
  }

  @Test
  @DisplayName("[3368] a product scope is not a route scope")
  void productScopeIsNotARoute() {
    assertThat(ApiKeyProduct.isProductScope("product:verikora")).isTrue();
    assertThat(ApiKeyProduct.isProductScope("ai:embed")).isFalse();
    assertThat(ApiKeyScopes.permits(List.of("product:verikora"), "GET", "/api/v1/anything"))
        .isTrue();
  }
}
