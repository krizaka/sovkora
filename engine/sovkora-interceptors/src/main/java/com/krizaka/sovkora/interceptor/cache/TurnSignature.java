package com.krizaka.sovkora.interceptor.cache;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What an embedding cannot be trusted with: the numbers of a turn and its negations.
 *
 * <p>"Refund 100" and "refund 1000", "I can cancel" and "I cannot cancel" sit almost on top of each
 * other in embedding space. A semantic hit therefore also needs equal signatures: the same numbers
 * (digits and spelled-out, English and French) and the same negations. The rule is conservative on
 * purpose — a false miss costs a model call, a false hit serves the wrong answer.
 */
public final class TurnSignature {

  private static final Pattern NUMBER = Pattern.compile("\\d++(?:[.,]\\d++)*+");
  private static final Pattern WORD = Pattern.compile("[\\p{L}']+");
  private static final Pattern MARKS = Pattern.compile("\\p{M}+");

  private static final Set<String> NEGATIONS =
      Set.of(
          "not",
          "no",
          "never",
          "none",
          "nobody",
          "nothing",
          "cannot",
          "without",
          "neither",
          "nor",
          "ne",
          "pas",
          "non",
          "jamais",
          "aucun",
          "aucune",
          "sans",
          "rien",
          "personne",
          "ni",
          "nul",
          "nulle",
          "plus");

  private static final Set<String> NUMBER_WORDS =
      Set.of(
          "zero",
          "one",
          "two",
          "three",
          "four",
          "five",
          "six",
          "seven",
          "eight",
          "nine",
          "ten",
          "eleven",
          "twelve",
          "twenty",
          "thirty",
          "forty",
          "fifty",
          "sixty",
          "seventy",
          "eighty",
          "ninety",
          "hundred",
          "thousand",
          "million",
          "un",
          "une",
          "deux",
          "trois",
          "quatre",
          "cinq",
          "sept",
          "huit",
          "neuf",
          "dix",
          "onze",
          "douze",
          "vingt",
          "trente",
          "quarante",
          "cinquante",
          "soixante",
          "cent",
          "cents",
          "mille");

  private TurnSignature() {}

  /**
   * The signature of a turn: its numbers and negations, folded and sorted, as one comparable
   * string.
   *
   * @param text the turn
   * @return the signature; equal for turns that differ in neither
   */
  public static String of(String text) {
    String folded = fold(text == null ? "" : text);
    List<String> parts = new ArrayList<>();
    Matcher numbers = NUMBER.matcher(folded);
    while (numbers.find()) {
      parts.add("n:" + numbers.group().replace(",", ".").replaceAll("\\.0+$", ""));
    }
    Matcher words = WORD.matcher(folded.replaceAll("\\d++(?:[.,]\\d++)*+", " "));
    while (words.find()) {
      String word = words.group();
      if (word.endsWith("n't")) {
        parts.add("x:not");
      } else if (NEGATIONS.contains(word)) {
        parts.add("x:" + ("cannot".equals(word) ? "not" : word));
      } else if (NUMBER_WORDS.contains(word)) {
        parts.add("w:" + word);
      }
    }
    Collections.sort(parts);
    return parts.stream().collect(Collectors.joining("|"));
  }

  private static String fold(String value) {
    return MARKS
        .matcher(Normalizer.normalize(value, Normalizer.Form.NFD))
        .replaceAll("")
        .toLowerCase(Locale.ROOT)
        .replace('’', '\'');
  }
}
