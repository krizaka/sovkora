package com.krizaka.sovkora.core.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PipelineExecutionExceptionTest {

  @Test
  void messageConstructor() {
    var ex = new PipelineExecutionException("pipeline failed");
    assertThat(ex.getMessage()).isEqualTo("pipeline failed");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void messageAndCauseConstructor() {
    var cause = new IllegalStateException("bad state");
    var ex = new PipelineExecutionException("wrapper", cause);
    assertThat(ex.getMessage()).isEqualTo("wrapper");
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void isRuntimeException() {
    assertThat(new PipelineExecutionException("test")).isInstanceOf(RuntimeException.class);
  }
}
