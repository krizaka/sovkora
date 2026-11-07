package com.krizaka.sovkora.core.domain.ports.outbound;

import java.util.Optional;

/**
 * Tier B crash-test: compiles or lints a code block in an isolated environment. The engine's own
 * structural check always runs; this port adds a real sandbox when one is wired.
 */
@FunctionalInterface
public interface CodeSandboxPort {

  /**
   * Crash-tests one code block.
   *
   * @param language the fence language, lower case, possibly empty
   * @param code the code block
   * @return the failure, or empty when the block passes
   */
  Optional<String> crashTest(String language, String code);
}
