package com.krizaka.sovkora.interceptor.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.infrastructure.support.SecurityContextUtil;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class UserContextResolverTest {

  @Test
  @DisplayName("intercept enriches user metadata if security data is empty")
  void interceptMetadataEmpty() {
    UserContextResolver resolver = new UserContextResolver();
    PromptContext context = new PromptContext("query", Map.of("initial", "val"));

    PromptContext result = resolver.intercept(context);
    assertThat(result).isNotNull();
    assertThat(result.rawUserQuery()).isEqualTo("query");
    assertThat(result.userMetadata()).containsOnly(Map.entry("initial", "val"));
  }

  @Test
  @DisplayName("intercept enriches user metadata when security data is present")
  void interceptMetadataWithSecurityData() {
    UserContextResolver resolver = new UserContextResolver();
    PromptContext context = new PromptContext("query", Map.of("initial", "val"));

    try (MockedStatic<SecurityContextUtil> mockSecurity = mockStatic(SecurityContextUtil.class)) {
      mockSecurity
          .when(SecurityContextUtil::extractSecurityMetadata)
          .thenReturn(Map.of("userId", "user-456", "email", "test@test.com"));

      PromptContext result = resolver.intercept(context);
      assertThat(result).isNotNull();
      assertThat(result.userMetadata())
          .containsEntry("initial", "val")
          .containsEntry("userId", "user-456")
          .containsEntry("email", "test@test.com");
    }
  }
}
