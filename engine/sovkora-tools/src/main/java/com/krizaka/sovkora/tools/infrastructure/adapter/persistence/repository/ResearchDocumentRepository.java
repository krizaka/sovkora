package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.ResearchDocumentEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.ResearchDocumentId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Documents research read, by URL and content hash. */
public interface ResearchDocumentRepository
    extends JpaRepository<ResearchDocumentEntity, ResearchDocumentId> {

  /**
   * The newest document read through a source for a URL, fetched no earlier than an instant.
   *
   * @param sourceId the source
   * @param url the URL as requested
   * @param notBefore the oldest acceptable fetch time
   * @return the document
   */
  Optional<ResearchDocumentEntity>
      findFirstByIdSourceIdAndIdUrlAndFetchedAtGreaterThanEqualOrderByFetchedAtDesc(
          UUID sourceId, String url, Instant notBefore);
}
