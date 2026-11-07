package com.krizaka.sovkora.tools.infrastructure.adapter.extraction;

import com.krizaka.sovkora.tools.domain.port.TextExtractor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Component;

/**
 * Readability-style extraction (ADR-087 §2): the page's main content as text, in reading order, one
 * paragraph per block, without what a reader does not read — scripts, styles, navigation, headers
 * and footers, forms, and anything hidden from the reader ({@code hidden}, {@code aria-hidden},
 * {@code display:none} or {@code visibility:hidden} however the CSS is spaced). Text a page hides
 * from people is how it would speak to the model behind their back, so it never reaches a quote.
 *
 * <p>The main content is {@code <main>}, else {@code <article>}, else {@code [role=main]}, else the
 * body. Its visible text is walked in document order: every block element (a heading, a paragraph,
 * a list item, a cell, a {@code div}…) starts and ends a paragraph, inline markup does not, and a
 * text is read once wherever it sits — a {@code div} beside a heading is not dropped, a paragraph
 * in a list item is not read twice.
 *
 * <p>Bytes are decoded as the server said: the {@code charset} of the answer, else a byte-order
 * mark or the page's {@code <meta charset>}, else UTF-8 — so an ISO-8859-1 page keeps its accents.
 * Plain text and XML are kept as they are, decoded the same way.
 */
@Component
class HtmlTextExtractor implements TextExtractor {

  /**
   * What a browser does not show. The style selectors are regular expressions: CSS spacing varies.
   */
  private static final String DROPPED =
      "script, style, noscript, template, iframe, svg, canvas, nav, header, footer, aside, form,"
          + " [hidden], [aria-hidden=true], [style~=(?i)display\\s*:\\s*none],"
          + " [style~=(?i)visibility\\s*:\\s*hidden]";

  @Override
  public Extracted extract(byte[] body, String contentType, String charset, URI base) {
    String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    if (!type.contains("html")) {
      return new Extracted("", normalise(decode(body, charset)));
    }
    Document page;
    try {
      // jsoup reads a byte-order mark first, then the charset given here, then <meta charset>.
      page = Jsoup.parse(new ByteArrayInputStream(body), supported(charset), base.toString());
    } catch (IOException impossible) {
      throw new UncheckedIOException("a byte array cannot fail to read", impossible);
    }
    String title = page.title();
    page.select(DROPPED).remove();
    Element main =
        first(
            page.selectFirst("main"),
            page.selectFirst("article"),
            page.selectFirst("[role=main]"),
            page.body());
    if (main == null) {
      return new Extracted(title, "");
    }
    return new Extracted(title, paragraphs(main));
  }

  /** The visible text of an element in reading order, one paragraph per block. */
  private static String paragraphs(Element root) {
    ParagraphWalker walker = new ParagraphWalker();
    root.traverse(walker);
    return walker.text();
  }

  /**
   * Walks a subtree in document order: text nodes join the current paragraph, a {@code br} breaks a
   * line, and every block element starts and ends a paragraph — so inline markup never splits one,
   * and a text is read once wherever it sits.
   */
  private static final class ParagraphWalker implements NodeVisitor {
    private final List<String> paragraphs = new ArrayList<>();
    private final StringBuilder current = new StringBuilder();

    @Override
    public void head(Node node, int depth) {
      if (node instanceof TextNode text) {
        current.append(text.text());
      } else if (node instanceof Element element) {
        if ("br".equals(element.normalName())) {
          current.append('\n');
        } else if (element.isBlock()) {
          flush();
        }
      }
    }

    @Override
    public void tail(Node node, int depth) {
      if (node instanceof Element element && element.isBlock()) {
        flush();
      }
    }

    private void flush() {
      String paragraph = normalise(current.toString());
      if (!paragraph.isEmpty()) {
        paragraphs.add(paragraph);
      }
      current.setLength(0);
    }

    String text() {
      flush();
      return String.join("\n\n", paragraphs);
    }
  }

  private static Element first(Element... candidates) {
    for (Element candidate : candidates) {
      if (candidate != null) {
        return candidate;
      }
    }
    return null;
  }

  /** A charset name the JVM knows, or {@code null} so jsoup reads it from the bytes. */
  private static String supported(String charset) {
    if (charset == null || charset.isBlank()) {
      return null;
    }
    try {
      return Charset.isSupported(charset.trim()) ? charset.trim() : null;
    } catch (IllegalCharsetNameException badName) {
      return null;
    }
  }

  /** The bytes as text: a byte-order mark, else the named charset, else UTF-8. */
  private static String decode(byte[] body, String charset) {
    if (body.length >= 3
        && (body[0] & 0xff) == 0xEF
        && (body[1] & 0xff) == 0xBB
        && (body[2] & 0xff) == 0xBF) {
      return new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
    }
    if (body.length >= 2 && (body[0] & 0xff) == 0xFE && (body[1] & 0xff) == 0xFF) {
      return new String(body, 2, body.length - 2, StandardCharsets.UTF_16BE);
    }
    if (body.length >= 2 && (body[0] & 0xff) == 0xFF && (body[1] & 0xff) == 0xFE) {
      return new String(body, 2, body.length - 2, StandardCharsets.UTF_16LE);
    }
    String named = supported(charset);
    try {
      return new String(body, named == null ? StandardCharsets.UTF_8 : Charset.forName(named));
    } catch (UnsupportedCharsetException unsupported) {
      return new String(body, StandardCharsets.UTF_8);
    }
  }

  private static String normalise(String text) {
    String collapsed = text.replace(' ', ' ').replaceAll("[ \\t\\x0B\\f\\r]++", " ");
    List<String> lines = new ArrayList<>();
    for (String line : collapsed.split("\n", -1)) {
      lines.add(trimSpaces(line));
    }
    return String.join("\n", lines).strip();
  }

  /** Removes the plain spaces at both ends of a line, and nothing else. */
  private static String trimSpaces(String line) {
    int start = 0;
    int end = line.length();
    while (start < end && line.charAt(start) == ' ') {
      start++;
    }
    while (end > start && line.charAt(end - 1) == ' ') {
      end--;
    }
    return line.substring(start, end);
  }
}
