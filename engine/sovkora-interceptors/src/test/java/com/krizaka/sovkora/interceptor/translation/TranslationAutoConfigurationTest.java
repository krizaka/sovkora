package com.krizaka.sovkora.interceptor.translation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TranslationAutoConfigurationTest {

  @Test
  @DisplayName("TranslationAutoConfiguration registers the LanguageAlignmentInterceptor bean")
  void registersBeans() {
    TranslationAutoConfiguration config = new TranslationAutoConfiguration();
    LanguageAlignmentInterceptor interceptor = config.languageAlignmentInterceptor();
    assertThat(interceptor).isNotNull();
  }
}
