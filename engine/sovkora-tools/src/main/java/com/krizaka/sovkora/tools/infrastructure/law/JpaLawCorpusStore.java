package com.krizaka.sovkora.tools.infrastructure.law;

import com.krizaka.sovkora.tools.application.port.law.LawCorpusStore;
import com.krizaka.sovkora.tools.domain.model.law.*;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawArticleVersionEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawArticleVersionId;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawImportEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawInstrumentEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.LawArticleVersionRepository;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.LawImportRepository;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.LawInstrumentRepository;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.LawRetrievalRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link LawCorpusStore} on the job-service's law tables, through Spring Data JPA on the engine's
 * own data source: no second connection pool (ADR-083). A change of text (close the old version,
 * store the new one) is one transaction, taken with a row lock on the open version, so two imports
 * cannot both replace it.
 */
@Component
class JpaLawCorpusStore implements LawCorpusStore {

  private static final int TOUCH_CHUNK = 500;
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final LawInstrumentRepository instruments;
  private final LawArticleVersionRepository versions;
  private final LawImportRepository imports;
  private final LawRetrievalRepository retrievals;
  private final EntityManager entityManager;
  private final TransactionTemplate transaction;

  JpaLawCorpusStore(
      LawInstrumentRepository instruments,
      LawArticleVersionRepository versions,
      LawImportRepository imports,
      LawRetrievalRepository retrievals,
      EntityManager entityManager,
      PlatformTransactionManager transactionManager) {
    this.instruments = Objects.requireNonNull(instruments, "LawInstrumentRepository");
    this.versions = Objects.requireNonNull(versions, "LawArticleVersionRepository");
    this.imports = Objects.requireNonNull(imports, "LawImportRepository");
    this.retrievals = Objects.requireNonNull(retrievals, "LawRetrievalRepository");
    this.entityManager = Objects.requireNonNull(entityManager, "EntityManager");
    this.transaction =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "PlatformTransactionManager"));
  }

  @Override
  public UUID beginImport(LawImportStart start) {
    LawImportEntity row = new LawImportEntity();
    row.setId(UUID.randomUUID());
    row.setSourceKey(start.sourceKey());
    row.setMode(start.mode().name());
    row.setDatasetUrl(start.datasetUrl());
    row.setDatasetSha256(start.datasetSha256());
    row.setImportedBy(start.importedBy());
    row.setStartedAt(start.startedAt());
    row.setStatus(LawImportStatus.RUNNING.name());
    imports.saveAndFlush(row);
    return row.getId();
  }

  @Override
  public void finishImport(UUID importId, LawImportResult result) {
    LawImportEntity row =
        imports
            .findById(importId)
            .orElseThrow(() -> new IllegalStateException("no import " + importId));
    row.setStatus(result.status().name());
    row.setDatasetEtag(result.datasetEtag());
    if (result.datasetSha256() != null) {
      row.setDatasetSha256(result.datasetSha256());
    }
    row.setConsolidatedTo(result.consolidatedTo());
    row.setArticlesNew(result.articlesNew());
    row.setArticlesChanged(result.articlesChanged());
    row.setArticlesUnchanged(result.articlesUnchanged());
    row.setFinishedAt(result.finishedAt());
    imports.saveAndFlush(row);
  }

  @Override
  public Optional<String> lastEtag(String sourceKey, String datasetUrl) {
    return imports
        .findFirstBySourceKeyAndDatasetUrlAndModeAndStatusOrderByStartedAtDesc(
            sourceKey, datasetUrl, "AUTOMATED", LawImportStatus.SUCCEEDED.name())
        .map(LawImportEntity::getDatasetEtag);
  }

  @Override
  public List<LawArticleVersion> currentVersions(String jurisdiction, String instrument) {
    return instruments
        .findByJurisdictionAndCode(jurisdiction, instrument)
        .map(
            row -> {
              List<LawArticleVersionEntity> open =
                  versions.findByIdInstrumentIdAndInForceToIsNull(row.getId());
              Map<UUID, String> urls = datasetUrls(open);
              return open.stream().map(version -> toDomain(row, version, urls)).toList();
            })
        .orElse(List.of());
  }

  @Override
  public void add(UUID importId, String sourceKey, LawArticle article, Instant seenAt) {
    UUID instrumentId = ensureInstrument(sourceKey, article);
    try {
      transaction.executeWithoutResult(
          status -> insertNew(open(instrumentId, importId, article, seenAt)));
    } catch (DataIntegrityViolationException | PersistenceException e) {
      throw new IllegalStateException(
          "another import stored "
              + article.instrument()
              + " "
              + article.ref()
              + " ("
              + article.language()
              + ") first",
          e);
    }
  }

  @Override
  public void replace(UUID importId, LawArticle previous, LawArticle next, Instant seenAt) {
    UUID instrumentId = existingInstrument(previous);
    try {
      transaction.executeWithoutResult(
          status -> {
            LawArticleVersionEntity open = lockExpected(instrumentId, previous);
            if (!next.inForceFrom().isAfter(open.getId().getInForceFrom())) {
              throw new IllegalStateException(
                  "the new text of "
                      + previous.instrument()
                      + " "
                      + previous.ref()
                      + " does not take effect after the one it replaces");
            }
            open.setInForceTo(next.inForceFrom());
            // The close is written before the insert: at most one version is open.
            versions.saveAndFlush(open);
            insertNew(open(instrumentId, importId, next, seenAt));
            markRetrievalsStale(previous);
          });
    } catch (DataIntegrityViolationException | PersistenceException e) {
      throw new IllegalStateException("the corpus refused the new version", e);
    }
  }

  @Override
  public void close(LawArticle previous, LocalDate inForceTo) {
    UUID instrumentId = existingInstrument(previous);
    transaction.executeWithoutResult(
        status -> {
          LawArticleVersionEntity open = lockExpected(instrumentId, previous);
          open.setInForceTo(inForceTo);
          versions.saveAndFlush(open);
          markRetrievalsStale(previous);
        });
  }

  @Override
  public void touch(
      String jurisdiction, String instrument, Collection<LawArticle> articles, Instant seenAt) {
    if (articles.isEmpty()) {
      return;
    }
    UUID instrumentId =
        instruments
            .findByJurisdictionAndCode(jurisdiction, instrument)
            .map(LawInstrumentEntity::getId)
            .orElseThrow(() -> new IllegalStateException("unknown instrument " + instrument));
    Map<String, List<String>> refsByLanguage = new HashMap<>();
    for (LawArticle article : articles) {
      refsByLanguage.computeIfAbsent(article.language(), l -> new ArrayList<>()).add(article.ref());
    }
    transaction.executeWithoutResult(
        status ->
            refsByLanguage.forEach(
                (language, refs) -> {
                  for (int from = 0; from < refs.size(); from += TOUCH_CHUNK) {
                    List<String> chunk =
                        refs.subList(from, Math.min(refs.size(), from + TOUCH_CHUNK));
                    versions.touchOpen(instrumentId, language, chunk, seenAt);
                  }
                }));
  }

  @Override
  public Optional<LawArticleVersion> inForceOn(
      String jurisdiction, String instrument, String ref, String language, LocalDate asOf) {
    return instruments
        .findByJurisdictionAndCode(jurisdiction, instrument)
        .flatMap(
            row ->
                versions
                    .findFirstByIdInstrumentIdAndIdRefAndIdLanguageAndIdInForceFromLessThanEqualOrderByIdInForceFromDesc(
                        row.getId(), ref, language, asOf)
                    .filter(v -> v.getInForceTo() == null || asOf.isBefore(v.getInForceTo()))
                    .map(version -> toDomain(row, version, datasetUrls(List.of(version)))));
  }

  /**
   * A cited article changed (a new version row, or the text left force): every cached retrieval
   * that cites it is stale, in the same transaction as the change, so the next call recomputes.
   */
  private void markRetrievalsStale(LawArticle changed) {
    String probe =
        "[{\"instrument\":"
            + quote(changed.instrument())
            + ",\"ref\":"
            + quote(changed.ref())
            + ",\"language\":"
            + quote(changed.language())
            + "}]";
    retrievals.markStaleCiting(changed.jurisdiction(), probe);
  }

  private static String quote(String value) {
    return JSON.writeValueAsString(value);
  }

  /** An insert, never an overwrite: saving a row whose key exists would update it silently. */
  private void insertNew(LawArticleVersionEntity row) {
    if (versions.existsById(row.getId())) {
      throw new IllegalStateException(
          "a version of this article already took effect on "
              + row.getId().getInForceFrom()
              + ", it is not overwritten");
    }
    // persist, never merge: a merge of a key that appeared meanwhile would update it silently.
    entityManager.persist(row);
    entityManager.flush();
  }

  private LawArticleVersionEntity lockExpected(UUID instrumentId, LawArticle expected) {
    LawArticleVersionEntity open =
        versions
            .lockOpen(instrumentId, expected.ref(), expected.language())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "another import already closed "
                            + expected.instrument()
                            + " "
                            + expected.ref()
                            + " ("
                            + expected.language()
                            + ")"));
    if (!open.getContentHash().equals(expected.contentHash().hex())
        || !open.getId().getInForceFrom().equals(expected.inForceFrom())) {
      throw new IllegalStateException(
          "the open version of "
              + expected.instrument()
              + " "
              + expected.ref()
              + " ("
              + expected.language()
              + ") is no longer the one that was compared");
    }
    return open;
  }

  private UUID ensureInstrument(String sourceKey, LawArticle article) {
    transaction.executeWithoutResult(
        status ->
            instruments.createIfAbsent(
                UUID.randomUUID(),
                article.jurisdiction(),
                article.instrument(),
                OfficialUrls.template(article.jurisdiction(), article.instrument()),
                sourceKey));
    return existingInstrument(article);
  }

  private UUID existingInstrument(LawArticle article) {
    return instruments
        .findByJurisdictionAndCode(article.jurisdiction(), article.instrument())
        .map(LawInstrumentEntity::getId)
        .orElseThrow(() -> new IllegalStateException("unknown instrument " + article.instrument()));
  }

  private static LawArticleVersionEntity open(
      UUID instrumentId, UUID importId, LawArticle article, Instant seenAt) {
    LawArticleVersionEntity row = new LawArticleVersionEntity();
    row.setId(
        new LawArticleVersionId(
            instrumentId, article.ref(), article.language(), article.inForceFrom()));
    row.setText(article.text());
    row.setContentHash(article.contentHash().hex());
    row.setImportId(importId);
    row.setFirstSeenAt(seenAt);
    row.setLastSeenAt(seenAt);
    return row;
  }

  /** The dataset address of each import that stored one of these versions, in one query. */
  private Map<UUID, String> datasetUrls(List<LawArticleVersionEntity> rows) {
    Map<UUID, String> urls = new HashMap<>();
    imports
        .findAllById(rows.stream().map(LawArticleVersionEntity::getImportId).distinct().toList())
        .forEach(row -> urls.put(row.getId(), row.getDatasetUrl()));
    return urls;
  }

  private static LawArticleVersion toDomain(
      LawInstrumentEntity instrument, LawArticleVersionEntity row, Map<UUID, String> urls) {
    String officialUrl =
        OfficialUrls.resolve(
            instrument.getOfficialUrlTemplate(), row.getId().getLanguage(), row.getId().getRef());
    String sourceUrl = urls.get(row.getImportId());
    LawArticle article =
        new LawArticle(
            instrument.getJurisdiction(),
            instrument.getCode(),
            row.getId().getRef(),
            row.getId().getLanguage(),
            row.getId().getInForceFrom(),
            row.getText(),
            officialUrl,
            sourceUrl == null ? officialUrl : sourceUrl);
    // The hash recorded with the row, never one recomputed here: comparing the two is how a reader
    // finds a text changed after its import (#89).
    return new LawArticleVersion(
        article,
        row.getFirstSeenAt(),
        row.getLastSeenAt(),
        row.getInForceTo(),
        row.getImportId(),
        row.getContentHash());
  }
}
