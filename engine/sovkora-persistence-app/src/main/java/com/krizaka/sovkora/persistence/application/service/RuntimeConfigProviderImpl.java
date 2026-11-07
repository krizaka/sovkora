package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.RuntimeConfigEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.RuntimeConfigRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Package-private implementation of {@link RuntimeConfigProvider} backed by the DB. */
@Service
@Transactional(readOnly = true)
class RuntimeConfigProviderImpl implements RuntimeConfigProvider {

  private final RuntimeConfigRepository repository;

  RuntimeConfigProviderImpl(RuntimeConfigRepository repository) {
    this.repository = Objects.requireNonNull(repository, "RuntimeConfigRepository cannot be null");
  }

  @Override
  public boolean getBoolean(String key, boolean defaultValue) {
    return rawValue(key).map(Boolean::parseBoolean).orElse(defaultValue);
  }

  @Override
  public int getInt(String key, int defaultValue) {
    Optional<String> raw = rawValue(key);
    if (raw.isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(raw.get().trim());
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  @Override
  public String getString(String key, String defaultValue) {
    return rawValue(key).orElse(defaultValue);
  }

  private Optional<String> rawValue(String key) {
    Objects.requireNonNull(key, "config key cannot be null");
    return repository.findById(key).map(RuntimeConfigEntity::getConfigValue);
  }
}
