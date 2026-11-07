package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import com.krizaka.sovkora.tools.domain.model.law.ContentHash;
import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.OfficialUrls;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads the articles of the consolidated XML published by Justice Canada and by Données Québec.
 *
 * <p>Both are read without network, from the stream given. The in-force date of an article comes
 * from the source ({@code date-eev} in Québec, {@code lims:inforce-start-date} federally); the
 * historical note is left out of the text and of its hash; a repealed article is not returned.
 */
public final class LawStatuteXmlParser {

  private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final Pattern REPEALED =
      Pattern.compile("^\\S+ [\\[(]?(?:Repealed|Abrog).{0,160}$", Pattern.DOTALL);

  /**
   * Parses a Québec consolidated statute (Données Québec, {@code Statutes\<code>\<LANG>}).
   *
   * @param in the XML, read to its end and not closed
   * @param instrument the instrument code, as in the dataset
   * @param language {@code fr} or {@code en}
   * @param sourceUrl the URL the XML came from, kept on each article
   * @return every section in force, with its reference and in-force date
   */
  public List<LawArticle> parseQuebec(
      InputStream in, String instrument, String language, String sourceUrl) {
    Document document = read(in);
    List<LawArticle> articles = new ArrayList<>();
    NodeList sections = document.getElementsByTagName("Section");
    for (int i = 0; i < sections.getLength(); i++) {
      Element section = (Element) sections.item(i);
      String code = section.getAttribute("Code");
      if (!code.startsWith("se:")) {
        continue;
      }
      String ref = code.substring("se:".length());
      String text = bodyText(section);
      if (isRepealed(text)) {
        continue;
      }
      String dated = required(section, "date-eev", ref);
      articles.add(
          new LawArticle(
              "QC",
              instrument,
              ref,
              language,
              LocalDate.parse(dated, BASIC_DATE),
              text,
              OfficialUrls.of("QC", instrument, language, ref),
              sourceUrl));
    }
    return articles;
  }

  /**
   * Parses a federal consolidated act (Justice Canada, {@code /XML/<code>.xml}).
   *
   * @param in the XML, read to its end and not closed
   * @param instrument the act code, such as {@code E-15}
   * @param language {@code fr} or {@code en}
   * @param sourceUrl the URL the XML came from, kept on each article
   * @return every section in force, with its label and in-force date
   */
  public List<LawArticle> parseJustice(
      InputStream in, String instrument, String language, String sourceUrl) {
    Document document = read(in);
    List<LawArticle> articles = new ArrayList<>();
    NodeList sections = document.getElementsByTagName("Section");
    for (int i = 0; i < sections.getLength(); i++) {
      Element section = (Element) sections.item(i);
      Element label = firstChild(section, "Label");
      if (label == null) {
        continue;
      }
      String ref = label.getTextContent().trim();
      String text = bodyText(section);
      if (isRepealed(text)) {
        continue;
      }
      String dated = required(section, "lims:inforce-start-date", ref);
      articles.add(
          new LawArticle(
              "CA",
              instrument,
              ref,
              language,
              LocalDate.parse(dated),
              text,
              OfficialUrls.of("CA", instrument, language, ref),
              sourceUrl));
    }
    return articles;
  }

  /**
   * The day a federal consolidation is current to, when the file says ({@code lims:current-date} on
   * the root element).
   *
   * @param xml the federal XML
   * @return the date, or empty when the root carries none
   */
  public Optional<LocalDate> federalConsolidatedTo(byte[] xml) {
    String value =
        read(new ByteArrayInputStream(xml)).getDocumentElement().getAttribute("lims:current-date");
    return value.isEmpty() ? Optional.empty() : Optional.of(LocalDate.parse(value));
  }

  /**
   * The text of a section as it is hashed: everything but the marginal note and the historical
   * note, normalised.
   *
   * @param section the section element
   * @return the normalised text
   */
  String bodyText(Element section) {
    Element copy = (Element) section.cloneNode(true);
    removeAll(copy, "HistoricalNote");
    removeAll(copy, "MarginalNote");
    return ContentHash.normalise(copy.getTextContent());
  }

  /**
   * Whether a section's text is only its repeal marker, such as {@code 106 (Abrogé).} or {@code 5
   * [Repealed, 2002, c. 22, s. 366]}.
   *
   * @param normalisedText the section text, normalised
   * @return true when the section is repealed
   */
  boolean isRepealed(String normalisedText) {
    return REPEALED.matcher(normalisedText).matches();
  }

  private static void removeAll(Element root, String name) {
    NodeList nodes = root.getElementsByTagName(name);
    List<Node> doomed = new ArrayList<>();
    for (int i = 0; i < nodes.getLength(); i++) {
      doomed.add(nodes.item(i));
    }
    for (Node node : doomed) {
      node.getParentNode().removeChild(node);
    }
  }

  private static Element firstChild(Element parent, String name) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element element && name.equals(element.getTagName())) {
        return element;
      }
    }
    return null;
  }

  private static String required(Element element, String attribute, String ref) {
    String value = element.getAttribute(attribute);
    if (value.isEmpty()) {
      throw new IllegalStateException("article " + ref + " has no " + attribute);
    }
    return value;
  }

  private static Document read(InputStream in) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      factory.setExpandEntityReferences(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      return factory.newDocumentBuilder().parse(in);
    } catch (ParserConfigurationException | SAXException | java.io.IOException e) {
      throw new IllegalStateException("the statute XML could not be read", e);
    }
  }
}
