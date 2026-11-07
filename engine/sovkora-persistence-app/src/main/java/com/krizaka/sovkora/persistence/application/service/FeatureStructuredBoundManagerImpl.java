package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.ports.inbound.FeatureStructuredBoundManager;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.FeatureStructuredBoundEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.FeatureStructuredBoundRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private implementation of the {@link FeatureStructuredBoundManager} inbound port. Not
 * cached: the bound is edited by an administrator and the next turn must see it.
 */
@Service
@Transactional(readOnly = true)
class FeatureStructuredBoundManagerImpl implements FeatureStructuredBoundManager {

  private static final int MAX_KEY_LENGTH = 120;

  private final FeatureStructuredBoundRepository repository;

  FeatureStructuredBoundManagerImpl(FeatureStructuredBoundRepository repository) {
    this.repository =
        Objects.requireNonNull(repository, "FeatureStructuredBoundRepository cannot be null");
  }

  @Override
  public OptionalInt maxAttempts(String featureKey) {
    if (featureKey != null && !featureKey.isBlank()) {
      var own = repository.findById(featureKey);
      if (own.isPresent()) {
        return OptionalInt.of(own.get().getMaxAttempts());
      }
    }
    return repository
        .findById(DEFAULT_FEATURE_KEY)
        .map(FeatureStructuredBoundEntity::getMaxAttempts)
        .map(OptionalInt::of)
        .orElseGet(OptionalInt::empty);
  }

  @Override
  public Map<String, Integer> all() {
    Map<String, Integer> bounds = new LinkedHashMap<>();
    repository.findAll().stream()
        .sorted(Comparator.comparing(FeatureStructuredBoundEntity::getFeatureKey))
        .forEach(row -> bounds.put(row.getFeatureKey(), row.getMaxAttempts()));
    return bounds;
  }

  @Override
  @Transactional
  public void upsert(String featureKey, int maxAttempts) {
    if (featureKey == null || featureKey.isBlank() || featureKey.length() > MAX_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "featureKey must be 1 to " + MAX_KEY_LENGTH + " characters");
    }
    if (maxAttempts < MIN_ATTEMPTS || maxAttempts > MAX_ATTEMPTS) {
      throw new IllegalArgumentException(
          "maxAttempts must be " + MIN_ATTEMPTS + " to " + MAX_ATTEMPTS);
    }
    FeatureStructuredBoundEntity row = new FeatureStructuredBoundEntity();
    row.setFeatureKey(featureKey);
    row.setMaxAttempts(maxAttempts);
    repository.save(row);
  }

  @Override
  @Transactional
  public boolean delete(String featureKey) {
    if (DEFAULT_FEATURE_KEY.equals(featureKey)) {
      throw new IllegalArgumentException("the default row cannot be removed");
    }
    if (featureKey == null || !repository.existsById(featureKey)) {
      return false;
    }
    repository.deleteById(featureKey);
    return true;
  }
}
