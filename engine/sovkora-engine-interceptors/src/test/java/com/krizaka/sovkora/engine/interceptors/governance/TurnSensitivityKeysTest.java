package com.krizaka.sovkora.engine.interceptors.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.krizaka.sovkora.engine.core.domain.model.chat.TurnSensitivity;
import com.krizaka.sovkora.jobs.api.domain.model.JobCommand;
import org.junit.jupiter.api.Test;

/**
 * The keys the engine reads to treat a turn as SENSITIVE are the ones the pack's controls write.
 */
class TurnSensitivityKeysTest {

  @Test
  void theKeysAreTheJobCommandsOwn() {
    assertEquals(JobCommand.SCOPE_REFUSED_TERMS_KEY, TurnSensitivity.scopeKey());
    assertEquals(JobCommand.SAFETY_CRISIS_TERMS_KEY, TurnSensitivity.safetyKey());
    assertEquals(JobCommand.GUARD_SUBJECT_KEY, TurnSensitivity.guardKey());
  }
}
