package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.LawFileReader;
import com.krizaka.sovkora.tools.application.port.law.LawSettings;
import com.krizaka.sovkora.tools.application.port.law.LawSource;
import com.krizaka.sovkora.tools.domain.model.law.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The manual import (ADR-089 §2): the traceable fallback when an official source cannot be read. An
 * administrator uploads the consolidated XML with the address it came from; the same parser and the
 * same comparison as the watcher run, with {@code mode = MANUAL}. The {@code law_import} row
 * records who imported it and the SHA-256 of the file, and it is refused without either.
 *
 * <p>Only an instrument of {@code law.import.instruments} is stored, and nothing while {@code
 * law.corpus.enabled} is false. The HTTP endpoint ({@code POST /api/v1/admin/law/imports}) belongs
 * to the host application that authenticates the administrator; it calls {@link
 * #importFile(ManualLawFile)}.
 */
public class LawManualImportService {

  private final List<LawSource> sources;
  private final LawSettings settings;
  private final LawFileReader reader;
  private final LawImportService importer;

  /**
   * Creates the service.
   *
   * @param sources the official sources, which give the {@code research_source} a file is recorded
   *     under
   * @param settings the {@code law.*} settings
   * @param reader the parser the importers use
   * @param importer the import of one source
   */
  public LawManualImportService(
      List<LawSource> sources,
      LawSettings settings,
      LawFileReader reader,
      LawImportService importer) {
    this.sources = List.copyOf(sources);
    this.settings = settings;
    this.reader = reader;
    this.importer = importer;
  }

  /**
   * Imports one uploaded file.
   *
   * @param file the file, who uploaded it and where it came from
   * @return what was unchanged, stored, requested and failed, with the import row written
   * @throws IllegalStateException when the corpus is off
   * @throws IllegalArgumentException when the instrument is not targeted, no source serves the
   *     jurisdiction, or the declared hash is not the file's
   */
  public LawWatchReport importFile(ManualLawFile file) {
    if (!settings.corpusEnabled()) {
      throw new IllegalStateException("the law corpus is off (law.corpus.enabled)");
    }
    String sha256 = file.sha256();
    if (file.declaredSha256() != null && !file.declaredSha256().equalsIgnoreCase(sha256)) {
      throw new IllegalArgumentException("the declared SHA-256 is not the file's");
    }
    boolean targeted =
        settings.importInstruments().stream()
            .anyMatch(
                target ->
                    target.jurisdiction().equals(file.jurisdiction())
                        && target.instrument().equals(file.instrument()));
    if (!targeted) {
      throw new IllegalArgumentException(
          file.jurisdiction()
              + ":"
              + file.instrument()
              + " is not in law.import.instruments, nothing else is stored");
    }
    LawSource official =
        sources.stream()
            .filter(source -> source.serves(file.jurisdiction()))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("no law source serves " + file.jurisdiction()));
    return importer.importFrom(
        new UploadedFile(official.sourceKey(), file, sha256, reader),
        file.jurisdiction(),
        List.of(LawTarget.wholeInstrument(file.jurisdiction(), file.instrument())),
        LawImportMode.MANUAL,
        file.importedBy());
  }

  /** The file as a source: it serves its own jurisdiction, in its own language. */
  private static final class UploadedFile implements LawSource {
    private final String sourceKey;
    private final ManualLawFile file;
    private final String sha256;
    private final LawFileReader reader;

    private UploadedFile(
        String sourceKey, ManualLawFile file, String sha256, LawFileReader reader) {
      this.sourceKey = sourceKey;
      this.file = file;
      this.sha256 = sha256;
      this.reader = reader;
    }

    @Override
    public String sourceKey() {
      return sourceKey;
    }

    @Override
    public boolean serves(String jurisdiction) {
      return file.jurisdiction().equals(jurisdiction);
    }

    @Override
    public String datasetUrl(String jurisdiction) {
      return file.sourceUrl();
    }

    @Override
    public Optional<String> knownSha256() {
      return Optional.of(sha256);
    }

    @Override
    public LawSnapshot fetch(
        String jurisdiction, Collection<LawTarget> targets, Optional<String> knownEtag) {
      List<LawArticle> articles =
          reader.read(
              file.jurisdiction(),
              file.instrument(),
              file.language(),
              file.content(),
              file.sourceUrl());
      return new LawSnapshot(
          articles,
          Set.of(file.language()),
          file.sourceUrl(),
          null,
          sha256,
          file.consolidatedTo(),
          false);
    }
  }
}
