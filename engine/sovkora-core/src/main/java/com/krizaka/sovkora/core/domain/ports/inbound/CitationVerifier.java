package com.krizaka.sovkora.core.domain.ports.inbound;

import com.krizaka.sovkora.core.domain.model.research.GroundedAnswer;
import com.krizaka.sovkora.core.domain.model.research.ReadDocument;
import com.krizaka.sovkora.core.domain.model.research.VerifyMode;
import java.util.List;

/**
 * Checks every claim of a grounded answer against the stored bytes it cites (ADR-087 §3), and
 * renders the answer that is served from the claims. Deterministic: no model is asked whether a
 * quote is right.
 */
public interface CitationVerifier {

  /**
   * The answer with each claim's status set from what was read, and its prose rendered from the
   * claims after verification.
   *
   * @param answer the model's answer in the citation shape
   * @param mode keep unverified claims marked, or drop them
   * @param read the documents grounded research read for this answer — a citation verifies against
   *     these and nothing else; empty when nothing was read, in which case no claim verifies
   * @return the verified answer
   */
  GroundedAnswer verify(GroundedAnswer answer, VerifyMode mode, List<ReadDocument> read);
}
