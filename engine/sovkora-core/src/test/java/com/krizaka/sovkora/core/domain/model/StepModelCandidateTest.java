package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StepModelCandidateTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n"})
  void rejectsInvalidModelName(String value) {
    assertThrows(IllegalArgumentException.class, () -> new StepModelCandidate(value, true, true));
  }

  @Test
  void preservesDeclaredMetadataWithoutInferringLocality() {
    StepModelCandidate candidate = new StepModelCandidate("ollama-looking-name", false, true);

    assertEquals("ollama-looking-name", candidate.modelName());
    assertFalse(candidate.sovereignLocal());
    assertTrue(candidate.available());
  }
}
