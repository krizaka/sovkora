package com.krizaka.sovkora.tools.domain.port;

import com.krizaka.sovkora.tools.domain.model.research.FetchAudit;
import com.krizaka.sovkora.tools.domain.model.research.FetchedDocument;
import com.krizaka.sovkora.tools.domain.model.research.ResearchSource;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Where grounded research keeps its sources, what it read, and its audit (ADR-087). */
public interface ResearchStore {

  /**
   * A source by its key.
   *
   * @param sourceKey the key
   * @return the source, if declared
   */
  Optional<ResearchSource> source(String sourceKey);

  /**
   * The newest document read through this source for this URL, no older than {@code notBefore}: a
   * hit is never served to another source, and the freshness is the requesting source's current
   * one.
   *
   * @param sourceId the requesting source
   * @param url the URL as requested
   * @param notBefore the oldest acceptable fetch time
   * @return the cached document, if any
   */
  Optional<FetchedDocument> freshDocument(UUID sourceId, String url, Instant notBefore);

  /**
   * The exact document a citation names — the bytes it was read from, whatever their age — as read
   * through one source: a document never answers through another source (ADR-087 §5).
   *
   * @param sourceId the source the document was read through
   * @param url the cited URL (the one asked for, or the final one)
   * @param contentHash the cited hash
   * @return the document, if research read it through that source
   */
  Optional<FetchedDocument> document(UUID sourceId, String url, String contentHash);

  /**
   * Stores a document once by URL and content hash.
   *
   * @param document what was read
   * @param requestedUrl the URL as requested (the lookup key; the document keeps its final URL)
   * @param sourceId the source that authorised every hop
   */
  void saveDocument(FetchedDocument document, String requestedUrl, UUID sourceId);

  /**
   * Writes one audit line.
   *
   * @param audit the line
   */
  void audit(FetchAudit audit);
}
