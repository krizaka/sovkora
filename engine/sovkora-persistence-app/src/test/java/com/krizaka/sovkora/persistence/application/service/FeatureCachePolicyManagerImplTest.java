package com.krizaka.sovkora.persistence.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.FeatureCachePolicyEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.FeatureCachePolicyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FeatureCachePolicyManagerImplTest {

  private final FeatureCachePolicyRepository repository = mock(FeatureCachePolicyRepository.class);
  private final FeatureCachePolicyManagerImpl manager =
      new FeatureCachePolicyManagerImpl(repository);

  private static FeatureCachePolicyEntity row(String key, String strategy) {
    var row = new FeatureCachePolicyEntity();
    row.setFeatureKey(key);
    row.setStrategy(strategy);
    return row;
  }

  @Test
  void aFeaturesOwnRowWinsOverTheDefault() {
    when(repository.findById("chat.support")).thenReturn(Optional.of(row("chat.support", "NONE")));

    assertEquals(Optional.of("NONE"), manager.strategy("chat.support"));
  }

  @Test
  void aFeatureWithoutARowReadsTheDefaultRow() {
    when(repository.findById("chat.other")).thenReturn(Optional.empty());
    when(repository.findById("*")).thenReturn(Optional.of(row("*", "SYSTEM_AND_TOOLS")));

    assertEquals(Optional.of("SYSTEM_AND_TOOLS"), manager.strategy("chat.other"));
    assertEquals(Optional.of("SYSTEM_AND_TOOLS"), manager.strategy(null));
  }

  @Test
  void withNoRowAtAllNothingIsAnswered() {
    when(repository.findById(any())).thenReturn(Optional.empty());

    assertTrue(manager.strategy("x").isEmpty());
  }

  @Test
  void upsertStoresAKnownStrategy() {
    manager.upsert("chat.support", "SYSTEM_ONLY");

    var saved = ArgumentCaptor.forClass(FeatureCachePolicyEntity.class);
    verify(repository).save(saved.capture());
    assertEquals("SYSTEM_ONLY", saved.getValue().getStrategy());
  }

  @Test
  void anUnknownStrategyOrABadKeyIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> manager.upsert("chat.support", "ALL"));
    assertThrows(IllegalArgumentException.class, () -> manager.upsert("chat.support", null));
    assertThrows(IllegalArgumentException.class, () -> manager.upsert(" ", "NONE"));
    assertThrows(IllegalArgumentException.class, () -> manager.upsert("x".repeat(121), "NONE"));
    verify(repository, never()).save(any());
  }

  @Test
  void allListsTheRowsSortedByKey() {
    when(repository.findAll()).thenReturn(List.of(row("b", "NONE"), row("*", "SYSTEM_ONLY")));

    assertEquals(List.of("*", "b"), List.copyOf(manager.all().keySet()));
  }

  @Test
  void deleteDropsAFeaturesRowButNeverTheDefault() {
    when(repository.existsById("chat.support")).thenReturn(true);

    assertTrue(manager.remove("chat.support"));
    assertFalse(manager.remove("unknown"));
    assertThrows(IllegalArgumentException.class, () -> manager.remove("*"));
  }
}
