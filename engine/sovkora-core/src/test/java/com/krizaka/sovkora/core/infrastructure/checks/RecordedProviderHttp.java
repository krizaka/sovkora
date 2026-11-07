package com.krizaka.sovkora.core.infrastructure.checks;

import com.krizaka.sovkora.core.domain.ports.outbound.CheckHttp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A provider's recorded answers, no network: the last name in the request body picks the answer.
 * Test support for the HTTP adapters.
 */
public final class RecordedProviderHttp implements CheckHttp {

  /** One request as it left the adapter. */
  public record Sent(String url, Map<String, String> headers, String body) {}

  public final List<Sent> sent = new ArrayList<>();
  private final Function<String, Reply> byBody;

  public RecordedProviderHttp(Function<String, Reply> byBody) {
    this.byBody = byBody;
  }

  /** The demo's recorded answers, one per outcome and per court category. */
  public static RecordedProviderHttp recorded() {
    return new RecordedProviderHttp(
        body -> {
          if (body.contains("\"Tremblay\"")) {
            return json(200, "match", "TAL_NON_PAYMENT", "case-tal-01");
          }
          if (body.contains("\"Morin\"")) {
            return json(200, "match", "CRIMINAL", "case-pen-02");
          }
          if (body.contains("\"Lavoie\"")) {
            return json(200, "match", "CIVIL", "case-civ-03");
          }
          if (body.contains("\"Gagnon\"")) {
            return json(200, "no_match", null, "case-none-04");
          }
          if (body.contains("\"Roy\"")) {
            return new Reply(502, "upstream said: Roy, Marie 1984-03-07");
          }
          if (body.contains("\"Bouchard\"")) {
            throw new TimeoutException();
          }
          if (body.contains("\"Pelletier\"")) {
            return json(200, "pending", null, "case-wait-05");
          }
          return new Reply(200, "<html>not json: Marie Inconnu</html>");
        });
  }

  static Reply json(int status, String result, String category, String reference) {
    String cat = category == null ? "" : ",\"category\":\"" + category + "\"";
    return new Reply(
        status, "{\"status\":\"" + result + "\"" + cat + ",\"reference\":\"" + reference + "\"}");
  }

  @Override
  public Reply post(String url, Map<String, String> headers, String body, Duration timeout) {
    sent.add(new Sent(url, headers, body));
    if (url.endsWith("/case-wait-05")) {
      return json(200, "no_match", null, "case-wait-05");
    }
    return byBody.apply(body);
  }
}
