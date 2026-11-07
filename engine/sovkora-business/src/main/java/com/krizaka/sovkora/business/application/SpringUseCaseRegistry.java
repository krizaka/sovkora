package com.krizaka.sovkora.business.application;

import com.krizaka.sovkora.business.api.Intention;
import com.krizaka.sovkora.business.api.UseCase;
import com.krizaka.sovkora.business.api.UseCaseDescriptor;
import com.krizaka.sovkora.business.api.UseCaseRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Auto-discovering {@link UseCaseRegistry}: it is handed every {@link UseCase} bean registered in
 * the context and resolves an {@link Intention} to the first use-case whose descriptor serves the
 * intention's capability. Adding a use-case is a new {@code UseCase} bean — nothing here changes.
 */
public final class SpringUseCaseRegistry implements UseCaseRegistry {

  private final List<UseCase<?, ?>> useCases;

  public SpringUseCaseRegistry(List<UseCase<?, ?>> useCases) {
    this.useCases = List.copyOf(Objects.requireNonNull(useCases, "useCases must not be null"));
  }

  @Override
  public Optional<UseCase<?, ?>> resolve(Intention intention) {
    Objects.requireNonNull(intention, "intention must not be null");
    return useCases.stream()
        .filter(useCase -> useCase.descriptor().capability() == intention.capability())
        .findFirst();
  }

  @Override
  public Collection<UseCaseDescriptor> all() {
    return useCases.stream().map(UseCase::descriptor).toList();
  }
}
