package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.structured.StructuredOutputRequest;

/**
 * Port (ADR-092 §4): asks a model for an answer of a given shape and returns it typed. Together
 * with {@link ChatGeneratorClient} (inference) and {@link EmbeddingClient} (embedding) it is what
 * the engine needs from a model library; {@code sovkora-adapter-spring-ai} implements all three.
 */
public interface StructuredOutputClient {

  /**
   * Generates an answer of the requested shape.
   *
   * @param request what to ask and the shape of the answer
   * @param <T> the shape
   * @return the answer, never {@code null}
   * @throws com.krizaka.sovkora.core.domain.model.structured.StructuredOutputInvalidException when
   *     no answer of that shape came within the attempts {@link StructuredOutputBounds} allows (an
   *     {@link IllegalStateException})
   */
  <T> T generate(StructuredOutputRequest<T> request);
}
