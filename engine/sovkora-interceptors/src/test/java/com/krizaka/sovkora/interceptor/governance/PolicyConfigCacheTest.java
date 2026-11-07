package com.krizaka.sovkora.interceptor.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.krizaka.sovkora.interceptor.governance.persistence.InterceptorPolicyStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PolicyConfigCacheTest {

  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final Logger logger = (Logger) LoggerFactory.getLogger(PolicyConfigCache.class);

  @BeforeEach
  void attach() {
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void detach() {
    logger.detachAppender(logs);
  }

  @Test
  @DisplayName("[#37] a failed read falls back to the last snapshot and logs the root cause")
  void logsTheThrowable() {
    InterceptorPolicyStore store = mock(InterceptorPolicyStore.class);
    when(store.findAllOrdered()).thenThrow(new IllegalStateException("connection timed out"));

    assertThat(new PolicyConfigCache(store).getPolicies()).isEmpty();

    assertThat(logs.list).hasSize(1);
    assertThat(logs.list.getFirst().getThrowableProxy()).isNotNull();
    assertThat(logs.list.getFirst().getThrowableProxy().getClassName())
        .isEqualTo(IllegalStateException.class.getName());
  }
}
