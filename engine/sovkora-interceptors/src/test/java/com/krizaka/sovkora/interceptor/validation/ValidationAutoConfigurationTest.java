package com.krizaka.sovkora.interceptor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidationAutoConfigurationTest {

  @Test
  @DisplayName(
      "ValidationAutoConfiguration registers CostShieldInterceptor and ClosedLoopValidationInterceptor")
  void registersBeans() {
    ValidationAutoConfiguration config = new ValidationAutoConfiguration();

    CostShieldInterceptor costShield = config.costShieldInterceptor();
    assertThat(costShield).isNotNull();

    ClosedLoopValidationProperties props =
        new ClosedLoopValidationProperties(true, 3, true, true, true, false);
    ProviderChatClient withModel = mock(ProviderChatClient.class);
    when(withModel.providers()).thenReturn(Set.of("openai"));
    ProviderChatClient withoutModel = mock(ProviderChatClient.class);
    when(withoutModel.providers()).thenReturn(Set.of());

    ClosedLoopValidationInterceptor advisorWithModel =
        config.closedLoopValidationInterceptor(props, withModel, null, null, null, null);
    assertThat(advisorWithModel).isNotNull();

    ClosedLoopValidationInterceptor advisorWithoutModel =
        config.closedLoopValidationInterceptor(props, withoutModel, null, null, null, null);
    assertThat(advisorWithoutModel).isNotNull();

    ModelTestShaperAdapter shaper = config.modelTestShaperAdapter(withModel);
    assertThat(shaper).isNotNull();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> config.modelTestShaperAdapter(withoutModel))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no ChatModel available");
  }
}
