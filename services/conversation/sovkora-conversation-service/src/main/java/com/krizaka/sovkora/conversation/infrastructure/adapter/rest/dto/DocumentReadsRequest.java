package com.krizaka.sovkora.conversation.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.conversation.domain.model.reader.DocumentPage;
import com.krizaka.sovkora.conversation.domain.model.reader.PageKind;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The body of {@code POST /api/v1/document-reads}: the pages and nothing else. There is no field
 * for a reader: the routing chooses it.
 *
 * @param pages the pages, in order
 */
public record DocumentReadsRequest(@Nullable List<Page> pages) {

  /** At most this many pages in one call: the routing's page budget cannot be set above it. */
  public static final int MAX_PAGES = 12;

  /**
   * @return the domain pages, or empty for a request that is not 1 to {@value #MAX_PAGES}
   *     well-formed pages
   */
  public Optional<List<DocumentPage>> toPages() {
    if (pages == null || pages.isEmpty() || pages.size() > MAX_PAGES) {
      return Optional.empty();
    }
    List<Optional<DocumentPage>> read =
        pages.stream()
            .map(page -> page == null ? Optional.<DocumentPage>empty() : page.toPage())
            .toList();
    return read.stream().allMatch(Optional::isPresent)
        ? Optional.of(read.stream().map(Optional::orElseThrow).toList())
        : Optional.empty();
  }

  /**
   * One page.
   *
   * @param image the page image, standard base64 (a PDF page rasterised by the product)
   * @param kind {@code pdf-scan}, {@code screenshot} or {@code photo}
   */
  public record Page(@Nullable String image, @Nullable String kind) {

    /**
     * @return the domain page, or empty for a page with no image, an unknown kind or an image that
     *     is not base64 within the bound
     */
    Optional<DocumentPage> toPage() {
      Optional<PageKind> known = kind == null ? Optional.empty() : PageKind.fromWire(kind);
      if (image == null || known.isEmpty()) {
        return Optional.empty();
      }
      try {
        return Optional.of(new DocumentPage(image, known.get()));
      } catch (IllegalArgumentException malformed) {
        return Optional.empty();
      }
    }
  }
}
