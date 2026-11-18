package com.krizaka.sovkora.conversation.infrastructure.adapter.reader;

import com.krizaka.sovkora.conversation.domain.model.reader.DocumentPage;
import com.krizaka.sovkora.conversation.domain.model.reader.DocumentReading;
import com.krizaka.sovkora.conversation.domain.model.reader.ReadRefusal;
import com.krizaka.sovkora.conversation.domain.model.reader.ReadResult;
import com.krizaka.sovkora.conversation.domain.model.reader.ReaderRouting;
import com.krizaka.sovkora.conversation.domain.port.DocumentReader;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The document readers over HTTP. Failure contract: <b>never throws</b>. A coded refusal of the
 * worker is that refusal; a worker that does not answer, answers without a known code (its own
 * token refusal among them) or answers what cannot be read is {@code READER_UNAVAILABLE}, which the
 * door answers 503: the door's wiring is at fault, not its caller. Nothing of a page is logged.
 */
final class HttpDocumentReaderAdapter implements DocumentReader {

  private static final Logger log = LoggerFactory.getLogger(HttpDocumentReaderAdapter.class);

  private final ReaderExchange exchange;

  HttpDocumentReaderAdapter(ReaderExchange exchange) {
    this.exchange = exchange;
  }

  @Override
  public ReadResult read(List<DocumentPage> pages, ReaderRouting routing) {
    ReaderWire.Ask ask =
        new ReaderWire.Ask(
            pages.stream()
                .map(page -> new ReaderWire.Page(page.image(), page.kind().wire()))
                .toList(),
            routing.settings());
    try {
      ReaderWire.Answer answer = exchange.read(ask);
      if (answer == null || answer.readings() == null || answer.readings().size() != pages.size()) {
        log.warn("The document reader answered no reading per page");
        return new ReadResult.Refused(ReadRefusal.READER_UNAVAILABLE);
      }
      return new ReadResult.Readings(
          answer.readings().stream().map(HttpDocumentReaderAdapter::reading).toList());
    } catch (RestClientResponseException refused) {
      ReadRefusal refusal = ReadRefusal.fromCode(codeOf(refused));
      log.debug("The document reader refused: status={} code={}", refused.getStatusCode(), refusal);
      return new ReadResult.Refused(refusal);
    } catch (RestClientException | IllegalArgumentException | NullPointerException down) {
      log.warn("The document reader is unavailable ({})", down.getClass().getSimpleName());
      return new ReadResult.Refused(ReadRefusal.READER_UNAVAILABLE);
    }
  }

  private static String codeOf(RestClientResponseException refused) {
    try {
      ReaderWire.Refusal body = refused.getResponseBodyAs(ReaderWire.Refusal.class);
      return body == null || body.code() == null ? "" : body.code();
    } catch (RuntimeException unreadable) {
      return "";
    }
  }

  private static DocumentReading reading(ReaderWire.Reading wire) {
    return new DocumentReading(
        wire.text(),
        wire.lines().stream()
            .map(
                line ->
                    new DocumentReading.Line(
                        line.text(),
                        line.box().left(),
                        line.box().top(),
                        line.box().right(),
                        line.box().bottom(),
                        line.confidence()))
            .toList(),
        wire.reader(),
        wire.version(),
        wire.generative(),
        wire.pageHash(),
        wire.key(),
        wire.costMicros(),
        wire.latencyMs(),
        wire.fallbackFrom());
  }
}
