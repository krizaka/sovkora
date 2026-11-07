package com.krizaka.sovkora.jobs.domain.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobExecutionExceptionTest {

  @Test
  @DisplayName("Checked, so a caller cannot forget the error path a failed job needs")
  void isChecked() {
    assertEquals(Exception.class, JobExecutionException.class.getSuperclass());
  }

  @Test
  void carriesTheMessageAndCause() {
    Throwable cause = new IllegalStateException("out of memory");
    JobExecutionException failure = new JobExecutionException("render failed", cause);

    assertEquals("render failed", failure.getMessage());
    assertSame(cause, failure.getCause());
    assertEquals("render failed", new JobExecutionException("render failed").getMessage());
  }
}
