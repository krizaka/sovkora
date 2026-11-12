package com.krizaka.sovkora.job.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.jobs.api.domain.model.CapabilityDeclaration;
import com.krizaka.sovkora.jobs.api.domain.port.JobExecutor;
import com.krizaka.sovkora.persistence.api.domain.ports.inbound.CapabilityManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutorCoherenceServiceTest {

  private static final List<String> OWN_BINDINGS =
      List.of("job.media.generate", "job.media.analyze", "job.text.*");

  @Test
  void aCapabilityDrainedByAnExternalWorkerIsNotThisProcessesToServe() {
    // The document checks route to job.validation.* — the Python pack worker drains them and never
    // reads a handler key. Reporting them as "no registered executor" was a false alarm (22 ERROR
    // lines at every dev startup) that sent the investigation to the image instead of the worker.
    ExecutorCoherenceService service =
        service(
            OWN_BINDINGS,
            capability(
                "orazaka.validation.document.screen",
                "validation.screen",
                "job.validation.screen"));

    assertThat(service.unservedCapabilities()).isEmpty();
  }

  @Test
  void aCapabilityRoutedHereWithoutAnExecutorIsReported() {
    CapabilityDeclaration broken =
        capability("orazaka.core.text.broken", "text.missing", "job.text.generate");
    ExecutorCoherenceService service =
        service(
            OWN_BINDINGS,
            broken,
            capability("orazaka.core.text.chat", "text.generate", "job.text.generate"));

    assertThat(service.unservedCapabilities()).containsExactly(broken);
  }

  @Test
  void aDisabledCapabilityIsNotReported() {
    CapabilityDeclaration off =
        new CapabilityDeclaration(
            "orazaka.core.text.off",
            "text.missing",
            "job.text.generate",
            "KILOTOKEN",
            "CHAT",
            "BATCH",
            "{}",
            "{}",
            false);

    assertThat(service(OWN_BINDINGS, off).unservedCapabilities()).isEmpty();
  }

  private static ExecutorCoherenceService service(
      List<String> bindings, CapabilityDeclaration... capabilities) {
    JobExecutor text = mock(JobExecutor.class);
    when(text.handlerKey()).thenReturn("text.generate");
    CapabilityManager manager = mock(CapabilityManager.class);
    when(manager.findAll()).thenReturn(List.of(capabilities));
    return new ExecutorCoherenceService(List.of(text), manager, bindings);
  }

  private static CapabilityDeclaration capability(
      String featureKey, String handlerKey, String routingKey) {
    return new CapabilityDeclaration(
        featureKey, handlerKey, routingKey, "KILOTOKEN", "CHAT", "BATCH", "{}", "{}", true);
  }
}
