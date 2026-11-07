package com.krizaka.sovkora.core.infrastructure.checks;

import com.krizaka.sovkora.core.domain.model.checks.CheckSubject;
import com.krizaka.sovkora.core.domain.ports.outbound.ExternalCheckConnector;
import com.krizaka.sovkora.core.domain.ports.outbound.ExternalCheckConnectorContract;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/** The mock holds to the connector contract, on every recorded fixture and an unknown name. */
class MockConnectorContractTest extends ExternalCheckConnectorContract {

  @Override
  protected ExternalCheckConnector connector() {
    return new MockConnector();
  }

  @Override
  protected List<CheckSubject> subjects() {
    return Stream.of("Tremblay", "Côté", "Lavoie", "Morin", "Gagnon", "Roy", "Bouchard", "Inconnu")
        .map(
            name ->
                new CheckSubject(
                    "subject-" + name.length(),
                    name,
                    "Marie",
                    LocalDate.of(1984, 3, 7),
                    "1234 rue Saint-Denis, Montréal"))
        .toList();
  }
}
