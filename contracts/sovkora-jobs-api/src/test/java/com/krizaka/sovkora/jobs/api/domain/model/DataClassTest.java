package com.krizaka.sovkora.jobs.api.api.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataClassTest {

  @Test
  void aProtectedJobIsKeptNotOneMomentPastTerminal() {
    assertEquals(Optional.of(Duration.ZERO), DataClass.SENSITIVE.jobRetentionAfterTerminal());
    assertEquals(Optional.of(Duration.ZERO), DataClass.REGULATED.jobRetentionAfterTerminal());
  }

  @Test
  void anOrdinaryJobIsNotPurgedByThisWindow() {
    assertEquals(Optional.empty(), DataClass.STANDARD.jobRetentionAfterTerminal());
  }

  @Test
  void onlyTheProtectedClassesSwitchOnTheControls() {
    assertFalse(DataClass.STANDARD.isProtected());
    assertTrue(DataClass.SENSITIVE.isProtected());
    assertTrue(DataClass.REGULATED.isProtected());
  }
}
