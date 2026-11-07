package com.krizaka.sovkora.core.infrastructure.checks;

import com.krizaka.sovkora.core.domain.model.checks.CheckPurpose;
import com.krizaka.sovkora.core.domain.model.checks.CheckSubject;
import com.krizaka.sovkora.core.domain.ports.outbound.ExternalCheckConnector;
import com.krizaka.sovkora.core.domain.ports.outbound.ExternalCheckConnectorContract;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** The Soquij adapter holds to the connector contract on the recorded answers, with no network. */
class SoquijConnectorContractTest extends ExternalCheckConnectorContract {

  @Override
  protected ExternalCheckConnector connector() {
    return HttpCheckConnector.soquij(RecordedProviderHttp.recorded(), Duration.ofSeconds(1));
  }

  @Override
  protected Set<CheckPurpose> purposes() {
    return HttpCheckConnector.soquij(RecordedProviderHttp.recorded(), Duration.ofSeconds(1))
        .supports();
  }

  @Override
  protected List<CheckSubject> subjects() {
    return Stream.of(
            "Tremblay", "Morin", "Lavoie", "Gagnon", "Roy", "Bouchard", "Pelletier", "Inconnu")
        .map(
            name ->
                new CheckSubject(
                    "subject-" + name,
                    name,
                    "Marie",
                    LocalDate.of(1984, 3, 7),
                    "1234 rue Saint-Denis, Montréal"))
        .toList();
  }
}
