package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.ports.inbound.FeatureToolBoundManager;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.FeatureToolBoundEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.FeatureToolBoundRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private implementation of the {@link FeatureToolBoundManager} inbound port. Not cached:
 * the bound is edited by an administrator and the next turn must see it.
 */
@Service
@Transactional(readOnly = true)
class FeatureToolBoundManagerImpl implements FeatureToolBoundManager {

  private static final int MAX_KEY_LENGTH = 120;

  private final FeatureToolBoundRepository repository;

  FeatureToolBoundManagerImpl(FeatureToolBoundRepository repository) {
    this.repository =
        Objects.requireNonNull(repository, "FeatureToolBoundRepository cannot be null");
  }

  @Override
  public OptionalInt maxToolRounds(String featureKey) {
    if (featureKey != null && !featureKey.isBlank()) {
      var own = repository.findById(featureKey);
      if (own.isPresent()) {
        return OptionalInt.of(own.get().getMaxToolRounds());
      }
    }
    return repository
        .findById(DEFAULT_FEATURE_KEY)
        .map(FeatureToolBoundEntity::getMaxToolRounds)
        .map(OptionalInt::of)
        .orElseGet(OptionalInt::empty);
  }

  @Override
  public Map<String, Integer> all() {
    Map<String, Integer> bounds = new LinkedHashMap<>();
    repository.findAll().stream()
        .sorted(Comparator.comparing(FeatureToolBoundEntity::getFeatureKey))
        .forEach(row -> bounds.put(row.getFeatureKey(), row.getMaxToolRounds()));
    return bounds;
  }

  @Override
  @Transactional
  public void upsert(String featureKey, int maxToolRounds) {
    if (featureKey == null || featureKey.isBlank() || featureKey.length() > MAX_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "featureKey must be 1 to " + MAX_KEY_LENGTH + " characters");
    }
    if (maxToolRounds < MIN_ROUNDS || maxToolRounds > MAX_ROUNDS) {
      throw new IllegalArgumentException(
          "maxToolRounds must be " + MIN_ROUNDS + " to " + MAX_ROUNDS);
    }
    FeatureToolBoundEntity row = new FeatureToolBoundEntity();
    row.setFeatureKey(featureKey);
    row.setMaxToolRounds(maxToolRounds);
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
