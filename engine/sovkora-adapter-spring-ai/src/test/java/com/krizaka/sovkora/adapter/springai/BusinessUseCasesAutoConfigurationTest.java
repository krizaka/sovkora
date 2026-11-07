package com.krizaka.sovkora.adapter.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ResourceLoader;

class BusinessUseCasesAutoConfigurationTest {

  @Test
  @DisplayName("Should create MarkdownPromptResolver bean successfully")
  void registersMarkdownPromptResolverBean() {
    var config = new BusinessUseCasesAutoConfiguration();
    var resourceLoader = mock(ResourceLoader.class);
    var resolver = config.markdownPromptResolver(config.promptTextSource(resourceLoader));
    assertThat(resolver).isNotNull();
  }
}
