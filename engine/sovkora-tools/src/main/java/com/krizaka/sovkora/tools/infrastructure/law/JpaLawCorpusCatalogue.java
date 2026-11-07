package com.krizaka.sovkora.tools.infrastructure.law;

import com.krizaka.sovkora.tools.application.port.law.LawCorpusCatalogue;
import com.krizaka.sovkora.tools.domain.model.law.LawImportSummary;
import com.krizaka.sovkora.tools.domain.model.law.LawInstrumentSummary;
import com.krizaka.sovkora.tools.domain.model.law.LawVersionSummary;
import com.krizaka.sovkora.tools.domain.model.law.OfficialUrls;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawImportEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawInstrumentEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.LawArticleVersionRepository;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.LawImportRepository;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.LawInstrumentRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link LawCorpusCatalogue} on the job-service law tables: counts, imports and the versions of an
 * article. It never returns an article's text.
 */
@Component
class JpaLawCorpusCatalogue implements LawCorpusCatalogue {

  private final LawInstrumentRepository instruments;
  private final LawArticleVersionRepository versions;
  private final LawImportRepository imports;

  JpaLawCorpusCatalogue(
      LawInstrumentRepository instruments,
      LawArticleVersionRepository versions,
      LawImportRepository imports) {
    this.instruments = Objects.requireNonNull(instruments, "LawInstrumentRepository");
    this.versions = Objects.requireNonNull(versions, "LawArticleVersionRepository");
    this.imports = Objects.requireNonNull(imports, "LawImportRepository");
  }

  @Override
  @Transactional(readOnly = true)
  public List<LawInstrumentSummary> instruments() {
    Map<UUID, long[]> counts = new HashMap<>();
    for (Object[] row : versions.countsByInstrument()) {
      counts.put(
          (UUID) row[0], new long[] {((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
    }
    return instruments.findAll(Sort.by("jurisdiction", "code")).stream()
        .map(
            i -> {
              long[] count = counts.getOrDefault(i.getId(), new long[2]);
              return new LawInstrumentSummary(
                  i.getJurisdiction(),
                  i.getCode(),
                  i.getTitleFr(),
                  i.getTitleEn(),
                  i.getSourceKey(),
                  count[0],
                  count[1]);
            })
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<LawImportSummary> imports(int limit) {
    return imports.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit)).stream()
        .map(JpaLawCorpusCatalogue::toSummary)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<LawVersionSummary> versions(
      String jurisdiction, String instrument, String ref, String language) {
    return instruments
        .findByJurisdictionAndCode(jurisdiction, instrument)
        .map(i -> history(i, ref, language))
        .orElse(List.of());
  }

  private List<LawVersionSummary> history(LawInstrumentEntity instrument, String ref, String lang) {
    String url = OfficialUrls.resolve(instrument.getOfficialUrlTemplate(), lang, ref);
    return versions
        .findByIdInstrumentIdAndIdRefAndIdLanguageOrderByIdInForceFromDesc(
            instrument.getId(), ref, lang)
        .stream()
        .map(
            v ->
                new LawVersionSummary(
                    v.getId().getInForceFrom(),
                    v.getInForceTo(),
                    v.getContentHash(),
                    url,
                    v.getImportId(),
                    v.getText().length()))
        .toList();
  }

  private static LawImportSummary toSummary(LawImportEntity row) {
    return new LawImportSummary(
        row.getId(),
        row.getSourceKey(),
        row.getMode(),
        row.getDatasetUrl(),
        row.getDatasetSha256(),
        row.getConsolidatedTo(),
        row.getImportedBy(),
        row.getStartedAt(),
        row.getFinishedAt(),
        row.getArticlesNew(),
        row.getArticlesChanged(),
        row.getArticlesUnchanged(),
        row.getStatus());
  }
}
