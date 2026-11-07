package com.krizaka.sovkora.core.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoreExceptionTest {

  @Test
  void messageOnlyConstructor() {
    var ex = new CoreException("something went wrong");
    assertThat(ex.getMessage()).isEqualTo("something went wrong");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void messageAndCauseConstructor() {
    var cause = new RuntimeException("root cause");
    var ex = new CoreException("wrapper", cause);
    assertThat(ex.getMessage()).isEqualTo("wrapper");
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void isRuntimeException() {
    assertThat(new CoreException("test")).isInstanceOf(RuntimeException.class);
  }
}
