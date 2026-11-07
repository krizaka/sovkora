package com.krizaka.sovkora.interceptor.translation;

import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.RoutingMode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LanguageAlignmentInterceptorTest {

  private final LanguageAlignmentInterceptor interceptor = new LanguageAlignmentInterceptor();

  @Test
  void intercept_userLanguageIsEnglish_returnsContextUnchanged() {
    var context = new PromptContext("hello", Map.of("preference.locale", "en"));
    PromptContext result = interceptor.intercept(context);
    assertSame(context, result);
  }

  @Test
  void intercept_noLanguageMetadata_returnsContextUnchanged() {
    var context = new PromptContext("hello", Map.of());
    PromptContext result = interceptor.intercept(context);
    assertSame(context, result);
  }

  @ParameterizedTest
  @CsvSource({
    "preference.locale, fr, French",
    "preference.locale, fr_CA, French",
    "preference.language, es, Spanish",
    "preference.locale, de, German",
    "preference.locale, pt, Portuguese",
    "preference.locale, it, Italian",
    "preference.locale, nl, Dutch",
    "preference.locale, ja, Japanese",
    "preference.locale, ko, Korean",
    "preference.locale, zh, Chinese",
    "preference.locale, ar, Arabic",
    "preference.locale, ru, Russian"
  })
  void intercept_supportedLocales_normalizeToLanguageName(
      String key, String value, String expectedLanguageName) {
    var context = new PromptContext("test", Map.of(key, value));
    PromptContext result = interceptor.intercept(context);
    String directive = (String) result.systemMetadata().get("languageAlignmentDirective");
    assertNotNull(directive);
    assertTrue(directive.contains(expectedLanguageName));
  }

  @Test
  void intercept_unknownLocale_usesRawTag() {
    var context = new PromptContext("test", Map.of("preference.locale", "sw"));
    PromptContext result = interceptor.intercept(context);

    String directive = (String) result.systemMetadata().get("languageAlignmentDirective");
    assertTrue(directive.contains("sw"));
  }

  @Test
  void intercept_localeWithDash_splitsCorrectly() {
    var context = new PromptContext("test", Map.of("preference.locale", "de-DE"));
    PromptContext result = interceptor.intercept(context);

    String directive = (String) result.systemMetadata().get("languageAlignmentDirective");
    assertTrue(directive.contains("German"));
  }

  @Test
  void intercept_preservesExistingSystemMetadata() {
    var context =
        new PromptContext(
            "test",
            Map.of("preference.locale", "fr"),
            Map.of("existingKey", "existingValue"),
            "test",
            null,
            RoutingMode.DETERMINISTIC);
    PromptContext result = interceptor.intercept(context);

    assertEquals("existingValue", result.systemMetadata().get("existingKey"));
    assertTrue(result.systemMetadata().containsKey("languageAlignmentDirective"));
  }
}
