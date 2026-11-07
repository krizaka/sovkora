package com.krizaka.sovkora.adapter.springai;

import com.krizaka.sovkora.test.architecture.ConfigBindingRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CFG-001, where the Spring beans are since the core went plain Java (sovkora-engine#121): the
 * binder builds the core's records here, so the guard that they bind unambiguously runs here.
 */
class ConfigBindingGuardTest {

  @Test
  @DisplayName(
      "[CFG-001] every type the configuration binder builds has a constructor it can choose")
  void configurationBindsUnambiguously() {
    ConfigBindingRules.assertConfigurationBindsUnambiguously();
  }
}
