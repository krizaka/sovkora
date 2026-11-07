package com.krizaka.sovkora.tools.domain.model.law;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The version of an article in force on a given day.
 *
 * @param text the normalised text (label and subsections)
 * @param contentHash the SHA-256 of the text, in lowercase hex
 * @param inForceFrom the day this text took effect
 * @param officialUrl the publisher's page, the address a citation shows
 * @param importId the {@code law_import} that brought this text in
 */
public record StatuteVersion(
    String text, String contentHash, LocalDate inForceFrom, String officialUrl, UUID importId) {}
