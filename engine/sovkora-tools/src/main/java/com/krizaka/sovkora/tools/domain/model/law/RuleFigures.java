package com.krizaka.sovkora.tools.domain.model.law;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The numbers a text states, by value: « 24 heures » and « vingt-quatre heures » both state 24, « 9
 * h » states 9, « trois mois » states 3. The support check of ADR-087 on a rule: every number the
 * rule's words state must be stated by one of its quotes. « un » and « one » are left out: they are
 * articles far more often than numbers.
 */
final class RuleFigures {

  private static final Pattern DIGITS = Pattern.compile("\\d+(?:[.,]\\d+)?");

  /** Longest words first, so « vingt-quatre » is read before « quatre ». */
  private static final Map<String, String> WORDS =
      Map.ofEntries(
          Map.entry("vingt-quatre", "24"),
          Map.entry("quarante-huit", "48"),
          Map.entry("soixante-douze", "72"),
          Map.entry("twenty-four", "24"),
          Map.entry("forty-eight", "48"),
          Map.entry("seventy-two", "72"),
          Map.entry("deux", "2"),
          Map.entry("trois", "3"),
          Map.entry("quatre", "4"),
          Map.entry("cinq", "5"),
          Map.entry("six", "6"),
          Map.entry("sept", "7"),
          Map.entry("huit", "8"),
          Map.entry("neuf", "9"),
          Map.entry("dix", "10"),
          Map.entry("onze", "11"),
          Map.entry("douze", "12"),
          Map.entry("quinze", "15"),
          Map.entry("vingt", "20"),
          Map.entry("trente", "30"),
          Map.entry("soixante", "60"),
          Map.entry("two", "2"),
          Map.entry("three", "3"),
          Map.entry("four", "4"),
          Map.entry("five", "5"),
          Map.entry("seven", "7"),
          Map.entry("eight", "8"),
          Map.entry("nine", "9"),
          Map.entry("ten", "10"),
          Map.entry("eleven", "11"),
          Map.entry("twelve", "12"),
          Map.entry("fifteen", "15"),
          Map.entry("twenty", "20"),
          Map.entry("thirty", "30"),
          Map.entry("sixty", "60"));

  private static final Pattern WORD;

  static {
    StringBuilder alternatives = new StringBuilder();
    WORDS.keySet().stream()
        .sorted((a, b) -> b.length() - a.length())
        .forEach(w -> alternatives.append(alternatives.isEmpty() ? "" : "|").append(w));
    WORD = Pattern.compile("(?<![\\p{L}-])(?:" + alternatives + ")(?![\\p{L}-])");
  }

  private RuleFigures() {}

  /**
   * The numbers a text states, as digit strings ({@code 24}, {@code 9}, {@code 3}).
   *
   * @param text any text, or null
   * @return the values, each once
   */
  static Set<String> of(String text) {
    Set<String> values = new HashSet<>();
    if (text == null) {
      return values;
    }
    String s = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    Matcher digits = DIGITS.matcher(s);
    while (digits.find()) {
      values.add(digits.group().replace(',', '.').replaceFirst("^0+(?=\\d)", ""));
    }
    Matcher words = WORD.matcher(s.replace('‑', '-'));
    while (words.find()) {
      values.add(WORDS.get(words.group()));
    }
    return values;
  }
}
