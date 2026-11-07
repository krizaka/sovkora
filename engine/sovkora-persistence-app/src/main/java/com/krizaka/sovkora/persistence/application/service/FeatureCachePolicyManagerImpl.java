package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.ports.inbound.FeatureCachePolicyManager;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.FeatureCachePolicyEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.FeatureCachePolicyRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private implementation of the {@link FeatureCachePolicyManager} inbound port. Not cached:
 * the policy is edited by an administrator and the next turn must see it.
 */
@Service
@Transactional(readOnly = true)
class FeatureCachePolicyManagerImpl implements FeatureCachePolicyManager {

  private static final int MAX_KEY_LENGTH = 120;

  private final FeatureCachePolicyRepository repository;

  FeatureCachePolicyManagerImpl(FeatureCachePolicyRepository repository) {
    this.repository =
        Objects.requireNonNull(repository, "FeatureCachePolicyRepository cannot be null");
  }

  @Override
  public Optional<String> strategy(String featureKey) {
    if (featureKey != null && !featureKey.isBlank()) {
      var own = repository.findById(featureKey);
      if (own.isPresent()) {
        return Optional.of(own.get().getStrategy());
      }
    }
    return repository.findById(DEFAULT_FEATURE_KEY).map(FeatureCachePolicyEntity::getStrategy);
  }

  @Override
  public Map<String, String> all() {
    Map<String, String> policy = new LinkedHashMap<>();
    repository.findAll().stream()
        .sorted(Comparator.comparing(FeatureCachePolicyEntity::getFeatureKey))
        .forEach(row -> policy.put(row.getFeatureKey(), row.getStrategy()));
    return policy;
  }

  @Override
  @Transactional
  public void upsert(String featureKey, String strategy) {
    if (featureKey == null || featureKey.isBlank() || featureKey.length() > MAX_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "featureKey must be 1 to " + MAX_KEY_LENGTH + " characters");
    }
    if (strategy == null || !STRATEGIES.contains(strategy)) {
      throw new IllegalArgumentException("strategy must be one of " + STRATEGIES);
    }
    FeatureCachePolicyEntity row = new FeatureCachePolicyEntity();
    row.setFeatureKey(featureKey);
    row.setStrategy(strategy);
    repository.save(row);
  }

  @Override
  @Transactional
  public boolean remove(String featureKey) {
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
