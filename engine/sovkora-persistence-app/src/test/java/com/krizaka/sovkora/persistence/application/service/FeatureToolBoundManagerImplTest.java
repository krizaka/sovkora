package com.krizaka.sovkora.persistence.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.FeatureToolBoundEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.FeatureToolBoundRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureToolBoundManagerImplTest {

  @Mock private FeatureToolBoundRepository repository;

  @InjectMocks private FeatureToolBoundManagerImpl manager;

  private static FeatureToolBoundEntity row(String feature, int rounds) {
    FeatureToolBoundEntity entity = new FeatureToolBoundEntity();
    entity.setFeatureKey(feature);
    entity.setMaxToolRounds(rounds);
    return entity;
  }

  @Test
  @DisplayName("a feature's own row wins over the default row")
  void ownRowWins() {
    when(repository.findById("agent.code.issue-to-pr"))
        .thenReturn(Optional.of(row("agent.code.issue-to-pr", 3)));

    assertEquals(OptionalInt.of(3), manager.maxToolRounds("agent.code.issue-to-pr"));
    verifyNoMoreInteractions(repository);
  }

  @Test
  @DisplayName("a feature with no row reads the default row")
  void defaultRow() {
    when(repository.findById("agent.other")).thenReturn(Optional.empty());
    when(repository.findById("*")).thenReturn(Optional.of(row("*", 8)));

    assertEquals(OptionalInt.of(8), manager.maxToolRounds("agent.other"));
  }

  @Test
  @DisplayName("a null or blank key reads the default row")
  void blankKeyReadsDefault() {
    when(repository.findById("*")).thenReturn(Optional.of(row("*", 8)));

    assertEquals(OptionalInt.of(8), manager.maxToolRounds(null));
    assertEquals(OptionalInt.of(8), manager.maxToolRounds(" "));
  }

  @Test
  @DisplayName("no own row and no default row is empty, the caller keeps its safe default")
  void nothingIsEmpty() {
    when(repository.findById("agent.other")).thenReturn(Optional.empty());
    when(repository.findById("*")).thenReturn(Optional.empty());

    assertTrue(manager.maxToolRounds("agent.other").isEmpty());
  }

  @Test
  @DisplayName("all() lists every bound by key, sorted, the default row included")
  void allSorted() {
    when(repository.findAll())
        .thenReturn(List.of(row("b.feature", 2), row("*", 8), row("a.feature", 4)));

    assertEquals(Map.of("*", 8, "a.feature", 4, "b.feature", 2), manager.all());
    assertEquals(List.of("*", "a.feature", "b.feature"), List.copyOf(manager.all().keySet()));
  }

  @Test
  @DisplayName("upsert stores the bound of a feature, within 1 to 32")
  void upsertStores() {
    manager.upsert("agent.code.issue-to-pr", 5);

    var saved = org.mockito.ArgumentCaptor.forClass(FeatureToolBoundEntity.class);
    verify(repository).save(saved.capture());
    assertEquals("agent.code.issue-to-pr", saved.getValue().getFeatureKey());
    assertEquals(5, saved.getValue().getMaxToolRounds());
  }

  @Test
  @DisplayName("upsert refuses a bound outside 1 to 32 and a blank or oversized key")
  void upsertValidates() {
    assertThrows(IllegalArgumentException.class, () -> manager.upsert("f", 0));
    assertThrows(IllegalArgumentException.class, () -> manager.upsert("f", 33));
    assertThrows(IllegalArgumentException.class, () -> manager.upsert(" ", 5));
    assertThrows(IllegalArgumentException.class, () -> manager.upsert(null, 5));
    assertThrows(IllegalArgumentException.class, () -> manager.upsert("x".repeat(121), 5));
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("delete removes a feature's own row and says whether there was one")
  void deleteRemoves() {
    when(repository.existsById("agent.a")).thenReturn(true);
    when(repository.existsById("agent.b")).thenReturn(false);

    assertTrue(manager.delete("agent.a"));
    assertFalse(manager.delete("agent.b"));
    verify(repository).deleteById("agent.a");
    verify(repository, never()).deleteById("agent.b");
  }

  @Test
  @DisplayName("the default row is never removed")
  void defaultRowStays() {
    assertThrows(IllegalArgumentException.class, () -> manager.delete("*"));
    verify(repository, never()).deleteById(any());
  }
}
