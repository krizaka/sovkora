package com.krizaka.sovkora.conversation.infrastructure.config;

import java.time.Duration;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The door to the document readers, mapped from {@code sovkora.document-reader}
 * (krizaka-internal#4198). The worker's address is Spring Boot's group property {@code
 * spring.http.serviceclient.reader.base-url} ({@code AI_READER_URL}). Which reader reads which page
 * is not here: it is the {@code document_reader_setting} rows.
 *
 * @param serviceToken the worker's service token ({@code AI_READER_SERVICE_TOKEN}); empty only for
 *     a worker on the loopback, which then demands none
 * @param readTimeout how long the worker may take for one call (default 45 seconds, under the
 *     edge's 50-second response timeout, so the caller gets this door's coded answer)
 * @param requiredRoles the roles any of which may use the door (default: administrators and service
 *     accounts, as at the checks' door); a caller with none is refused 403
 * @param ratePerMinute calls one actor may make in a minute (default 60)
 */
@ConfigurationProperties(prefix = "sovkora.document-reader")
public record DocumentReaderProperties(
    @Nullable String serviceToken,
    @Nullable Duration readTimeout,
    @Nullable Set<String> requiredRoles,
    int ratePerMinute) {

  /** Fills the defaults of what a deployment left out. */
  public DocumentReaderProperties {
    serviceToken = serviceToken == null ? "" : serviceToken.strip();
    readTimeout = readTimeout == null ? Duration.ofSeconds(45) : readTimeout;
    requiredRoles =
        requiredRoles == null || requiredRoles.isEmpty()
            ? Set.of("ROLE_ADMIN", "ROLE_SERVICE")
            : Set.copyOf(requiredRoles);
    ratePerMinute = ratePerMinute <= 0 ? 60 : ratePerMinute;
  }
}
