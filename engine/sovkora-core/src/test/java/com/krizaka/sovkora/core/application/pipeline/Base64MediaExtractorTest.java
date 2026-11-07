package com.krizaka.sovkora.core.application.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Base64MediaExtractorTest {

  @ParameterizedTest
  @CsvSource(
      value = {
        "null, '', true",
        "'', '', true",
        "'Analyze this image', 'Analyze this image', true",
        "'Analyze [posterBase64: dGVzdA== and more', 'Analyze [posterBase64: dGVzdA== and more', true",
        "'Analyze [posterBase64: !!!invalid!!!] now', 'Analyze now', true"
      },
      nullValues = {"null"})
  void extract_variousInputs_returnExpectedCleanedQueryAndNoMedia(
      String input, String expectedQuery, boolean expectEmptyMedia) {
    var result = Base64MediaExtractor.extract(input);
    assertEquals(expectedQuery, result.cleanedQuery());
    if (expectEmptyMedia) {
      assertTrue(result.imageBytes().isEmpty());
    }
  }

  @Test
  void extract_validBase64Marker_extractsBytesAndCleansQuery() {
    byte[] original = {1, 2, 3, 4, 5};
    String encoded = Base64.getEncoder().encodeToString(original);
    String raw = "Analyze [posterBase64: " + encoded + "] please";

    var result = Base64MediaExtractor.extract(raw);
    assertEquals("Analyze please", result.cleanedQuery());
    assertTrue(result.imageBytes().isPresent());
    assertArrayEquals(original, result.imageBytes().get());
  }

  @Test
  void extract_oversizedPayload_returnsCleansQueryWithNoMedia() {
    // Create a base64 string that would decode to > 10MB
    StringBuilder huge = new StringBuilder();
    String chunk = Base64.getEncoder().encodeToString(new byte[1024]);
    for (int i = 0; i < 15000; i++) {
      huge.append(chunk);
    }
    var result = Base64MediaExtractor.extract("Q [posterBase64: " + huge + "] end");
    assertEquals("Q end", result.cleanedQuery());
    assertTrue(result.imageBytes().isEmpty());
  }

  @Test
  void extract_markerAtStartOfQuery_cleanedQueryStartsClean() {
    byte[] data = {10, 20, 30};
    String encoded = Base64.getEncoder().encodeToString(data);
    var result = Base64MediaExtractor.extract("[posterBase64: " + encoded + "] rest of query");
    assertEquals("rest of query", result.cleanedQuery());
    assertTrue(result.imageBytes().isPresent());
  }

  @Test
  void extract_markerAtEndOfQuery_cleanedQueryEndsClean() {
    byte[] data = {10, 20, 30};
    String encoded = Base64.getEncoder().encodeToString(data);
    var result = Base64MediaExtractor.extract("query text [posterBase64: " + encoded + "]");
    assertEquals("query text", result.cleanedQuery());
    assertTrue(result.imageBytes().isPresent());
  }
}
