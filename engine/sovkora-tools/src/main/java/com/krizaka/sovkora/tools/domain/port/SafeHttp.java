package com.krizaka.sovkora.tools.domain.port;

import com.krizaka.sovkora.tools.domain.model.research.FetchLimits;
import com.krizaka.sovkora.tools.domain.model.research.FetchRefusal;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * An HTTP GET that cannot be pointed at the inside (ADR-087 §4): one resolution per hop, the
 * connection pinned to the address that was checked, every redirect checked again — by the address
 * policy and by {@code hopCheck} — and one time cap for the whole call, hops and body included,
 * beside the size cap.
 */
public interface SafeHttp {

  /**
   * An answer read within the caps.
   *
   * @param finalUri the URI after redirects
   * @param status the status
   * @param contentType the media type without parameters, or empty
   * @param charset the {@code charset} parameter of the Content-Type, or {@code null} when the
   *     answer named none — kept so the extractor decodes the bytes as the server said
   * @param body the body, at most {@code maxBytes}
   * @param etag the ETag, or {@code null}
   * @param lastModified the Last-Modified, or {@code null}
   */
  record Response(
      URI finalUri,
      int status,
      String contentType,
      String charset,
      byte[] body,
      String etag,
      String lastModified) {

    @Override
    public boolean equals(Object other) {
      return other instanceof Response that
          && status == that.status
          && Objects.equals(finalUri, that.finalUri)
          && Objects.equals(contentType, that.contentType)
          && Objects.equals(charset, that.charset)
          && Arrays.equals(body, that.body)
          && Objects.equals(etag, that.etag)
          && Objects.equals(lastModified, that.lastModified);
    }

    @Override
    public int hashCode() {
      return 31 * Objects.hash(finalUri, status, contentType, charset, etag, lastModified)
          + Arrays.hashCode(body);
    }

    @Override
    public String toString() {
      return "Response[finalUri="
          + finalUri
          + ", status="
          + status
          + ", contentType="
          + contentType
          + ", body="
          + (body == null ? "null" : body.length + " bytes")
          + "]";
    }
  }

  /**
   * The answer, or why there is none.
   *
   * @param response the answer
   * @param refusal the reason
   */
  record Outcome(Optional<Response> response, Optional<FetchRefusal> refusal) {}

  /**
   * Reads a URI.
   *
   * @param uri the first hop
   * @param limits the caps and the user agent; {@code timeoutMs} bounds the whole call
   * @param hopCheck applied to every hop, the first included: a reason refuses the hop
   * @return the outcome
   */
  Outcome get(URI uri, FetchLimits limits, Function<URI, Optional<FetchRefusal>> hopCheck);
}
